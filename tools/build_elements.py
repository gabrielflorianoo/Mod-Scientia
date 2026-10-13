#!/usr/bin/env python3
"""Builds src/main/resources/data/scientia/science/elements.json.

Input: a periodic-table dataset in the format of Bowserinator/Periodic-Table-JSON
(https://github.com/Bowserinator/Periodic-Table-JSON, values compiled from Wikipedia, CC BY-SA).
Only numeric/physical facts are taken from it; every value was spot-checked against standard
references (IUPAC standard atomic weights 2021 abridged, CRC Handbook-style values). Descriptions are
written for this project. Properties of superheavy elements (Z >= 100) are not used because they are
predictions or unmeasured; they are stored as null and flagged.

Usage: python3 tools/build_elements.py PeriodicTableJSON.json
"""
import json
import sys
from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/data/scientia/science/elements.json"

CATEGORY = {
    "alkali metal": "alkali_metal", "alkaline earth metal": "alkaline_earth_metal",
    "transition metal": "transition_metal", "post-transition metal": "post_transition_metal",
    "metalloid": "metalloid", "diatomic nonmetal": "reactive_nonmetal", "polyatomic nonmetal": "reactive_nonmetal",
    "noble gas": "noble_gas", "lanthanide": "lanthanide", "actinide": "actinide",
}
# Elements with no stable isotopes and no IUPAC standard atomic weight: the value is a mass number.
MASS_NUMBER = {43, 61, 84, 85, 86, 87, 88, 89} | set(range(93, 119))
FROM_DECAY = {84, 85, 86, 87, 88, 89, 91}
TRACE_NATURAL = {43, 61, 93, 94}  # occur in trace amounts from natural fission / neutron capture
ESTIMATED = {85: "Bulk astatine has never been observed; density and melting point are estimates.",
             87: "Bulk francium has never been observed; its melting point and density are estimates.",
             97: "Measured on microgram-scale samples; values are uncertain.",
             98: "Measured on microgram-scale samples; values are uncertain.",
             99: "Measured on microgram-scale samples; values are uncertain."}

# Common oxidation states (main ones, IUPAC/Greenwood & Earnshaw conventions); 0 omitted.
OX = {
 1:[1,-1],2:[],3:[1],4:[2],5:[3],6:[4,-4,2],7:[-3,3,5],8:[-2],9:[-1],10:[],11:[1],12:[2],13:[3],14:[4,-4],
 15:[-3,3,5],16:[-2,4,6],17:[-1,1,5,7],18:[],19:[1],20:[2],21:[3],22:[4,3],23:[5,4,3],24:[3,6,2],25:[2,4,7],
 26:[2,3],27:[2,3],28:[2],29:[2,1],30:[2],31:[3],32:[4,2],33:[3,5,-3],34:[-2,4,6],35:[-1,1,5],36:[2],37:[1],
 38:[2],39:[3],40:[4],41:[5],42:[6,4],43:[7,4],44:[3,4],45:[3],46:[2,4],47:[1],48:[2],49:[3],50:[4,2],51:[3,5,-3],
 52:[-2,4,6],53:[-1,1,5,7],54:[2,4,6],55:[1],56:[2],57:[3],58:[3,4],59:[3],60:[3],61:[3],62:[3,2],63:[3,2],64:[3],
 65:[3],66:[3],67:[3],68:[3],69:[3],70:[3,2],71:[3],72:[4],73:[5],74:[6,4],75:[7,4],76:[4,8],77:[3,4],78:[2,4],
 79:[3,1],80:[2,1],81:[1,3],82:[2,4],83:[3],84:[2,4],85:[-1,1],86:[2],87:[1],88:[2],89:[3],90:[4],91:[5],92:[6,4],
 93:[5],94:[4],95:[3],96:[3],97:[3],98:[3],99:[3],100:[3],101:[3],102:[2],103:[3],
}

NOTES = {
 "H": "The lightest element and the most abundant in the universe. Its single proton and electron make it the starting point for atomic models; water (H₂O) and most organic molecules contain it.",
 "He": "A noble gas with a completely filled first electron shell, so it is chemically inert. It is formed in stars by nuclear fusion and on Earth by radioactive alpha decay.",
 "Li": "The lightest metal. Like all alkali metals it readily loses one electron; lithium compounds are central to rechargeable batteries.",
 "Be": "A light, stiff alkaline-earth metal found in the mineral beryl (emerald is a gem variety of beryl).",
 "B": "A metalloid that forms strong covalent networks; borosilicate laboratory glass contains boron oxide.",
 "C": "Forms more compounds than any other element because carbon atoms bond strongly to each other. Graphite and diamond are two allotropes with very different properties: graphite conducts electricity, diamond is an electrical insulator.",
 "N": "Makes up about 78 % of the air by volume as N₂ molecules held together by a very strong triple bond.",
 "O": "About 21 % of air by volume and the most abundant element in Earth's crust by mass, mostly bound in silicate and oxide minerals.",
 "F": "The most electronegative element; it reacts with almost every other element.",
 "Ne": "A noble gas; electric discharges through neon produce its characteristic red-orange glow.",
 "Na": "A soft, very reactive alkali metal. It never occurs free in nature; sodium chloride (table salt) is its best-known compound.",
 "Mg": "A light alkaline-earth metal; the magnesium ion is at the centre of the chlorophyll molecule.",
 "Al": "The most abundant metal in Earth's crust. A thin oxide layer protects it from further corrosion.",
 "Si": "A metalloid and semiconductor, the second most abundant element in the crust. Sand and quartz are mostly silicon dioxide (SiO₂).",
 "P": "Essential for life (DNA, ATP, bone mineral). Elemental phosphorus exists as several allotropes with very different reactivity.",
 "S": "A yellow non-metal found near volcanoes and in sulfide ores such as chalcopyrite (CuFeS₂).",
 "Cl": "A reactive halogen. Chlorine gas is toxic, but chloride ions are harmless in table salt and essential to living things.",
 "Ar": "The most abundant noble gas in air (about 0.93 %), produced by radioactive decay of potassium-40.",
 "K": "An alkali metal essential to nerve signalling. Potassium salts give a lilac flame.",
 "Ca": "An alkaline-earth metal. Limestone, calcite, chalk and dripstone are calcium carbonate; bones contain calcium phosphate minerals.",
 "Ti": "A strong, low-density transition metal resistant to corrosion.",
 "Cr": "A hard transition metal used in stainless steel and chrome plating.",
 "Mn": "A transition metal important in steel-making and found in many oxidation states.",
 "Fe": "The most common element on Earth by mass (much of the core is iron). Iron is smelted from oxide ores such as hematite (Fe₂O₃) and magnetite (Fe₃O₄) and is ferromagnetic.",
 "Co": "A ferromagnetic transition metal used in magnets and as the blue colouring of glass and ceramics.",
 "Ni": "A ferromagnetic, corrosion-resistant metal used in stainless steel and coins.",
 "Cu": "A reddish metal with the second-highest electrical conductivity of any element (after silver), which is why electrical wiring is made of copper.",
 "Zn": "Used to galvanise steel against corrosion and in brass (a copper–zinc alloy).",
 "Ga": "A metal that melts at about 30 °C, just above room temperature.",
 "Ge": "A semiconductor metalloid used in early transistors and in infrared optics.",
 "As": "A metalloid; many arsenic compounds are highly toxic.",
 "Br": "One of only two elements that are liquid at room temperature (with mercury).",
 "Kr": "A noble gas used in some lighting.",
 "Sr": "Strontium salts give fireworks a bright red colour.",
 "Ag": "The best electrical and thermal conductor of all metals.",
 "Sn": "A soft metal used in solder and to coat (tin-plate) steel; bronze is a copper–tin alloy.",
 "I": "A halogen that forms violet vapour when heated; iodide is needed for thyroid hormones.",
 "Xe": "A noble gas that, unusually, forms some stable compounds with fluorine and oxygen.",
 "Cs": "A very reactive alkali metal; the SI second is defined using a transition of caesium-133 atoms.",
 "Ba": "Barium compounds give a green flame; barium sulfate is used in medical X-ray imaging.",
 "W": "Tungsten has the highest melting point of all metals, which is why it was used for light-bulb filaments.",
 "Pt": "A dense, unreactive precious metal and important catalyst.",
 "Au": "A dense, very unreactive precious metal that is found as native (uncombined) metal. It is an excellent conductor and does not tarnish.",
 "Hg": "The only metal that is liquid at room temperature. Mercury and its compounds are toxic.",
 "Pb": "A dense, soft metal. Lead is toxic, so its former uses in paint and petrol have been phased out.",
 "Bi": "The heaviest element with a practically stable isotope (bismuth-209 decays with an extremely long half-life).",
 "Rn": "A radioactive noble gas formed by the decay of radium; it can accumulate in poorly ventilated buildings.",
 "Ra": "Radioactive alkaline-earth metal discovered by Marie and Pierre Curie.",
 "U": "A dense radioactive actinide that occurs naturally; uranium-235 can sustain nuclear fission.",
 "Pu": "A radioactive actinide produced in nuclear reactors; only trace amounts occur naturally.",
 "Tc": "The lightest element with no stable isotopes; most technetium is produced artificially.",
 "Pm": "A lanthanide with no stable isotopes.",
 "Og": "The heaviest element yet made; only a handful of atoms have ever been produced.",
}

PHASE = {"Solid": "solid", "Liquid": "liquid", "Gas": "gas"}
CAT_TEXT = {"alkali_metal": "an alkali metal", "alkaline_earth_metal": "an alkaline-earth metal",
            "transition_metal": "a transition metal", "post_transition_metal": "a post-transition metal",
            "metalloid": "a metalloid", "reactive_nonmetal": "a reactive non-metal", "noble_gas": "a noble gas",
            "lanthanide": "a lanthanide", "actinide": "an actinide", "unknown": "an element whose chemical properties are not yet known"}


def block_of(z, group):
    if 57 <= z <= 71 or 89 <= z <= 103:
        return "d" if z in (71, 103) else "f"
    if group in (1, 2) or z == 2:
        return "s"
    if 3 <= group <= 12:
        return "d"
    return "p"


def main(src):
    data = json.load(open(src, encoding="utf-8"))["elements"]
    out = []
    for e in data:
        z = e["number"]
        if z > 118:
            continue
        cat_raw = e["category"]
        category = CATEGORY.get(cat_raw, "unknown")
        predicted = z >= 100
        group = None if (57 <= z <= 71 or 89 <= z <= 103) else e["group"]
        if z in (57, 89):
            group = 3  # IUPAC still debates La/Ac vs Lu/Lr in group 3; the layout places them in the f-block row.
        config = (e.get("electron_configuration_semantic") or "").replace("*", "").strip()
        metal = category in ("alkali_metal", "alkaline_earth_metal", "transition_metal", "post_transition_metal",
                             "lanthanide", "actinide")
        if predicted or category == "unknown":
            elec, therm = "unknown", "unknown"
        elif metal:
            elec, therm = "conductor", "good"
        elif category == "metalloid":
            elec, therm = "semiconductor", "moderate"
        elif z == 6:
            elec, therm = "conductor (graphite) / insulator (diamond)", "very high (diamond) / anisotropic (graphite)"
        else:
            elec, therm = "insulator", "poor"
        if z in FROM_DECAY:
            occ = "from_decay"
        elif z in TRACE_NATURAL:
            occ = "trace_natural"
        elif z >= 95:
            occ = "synthetic"
        else:
            occ = "primordial"
        density = None if predicted else e.get("density")
        melt = None if predicted else e.get("melt")
        boil = None if predicted else e.get("boil")
        note_parts = []
        if z in ESTIMATED:
            note_parts.append(ESTIMATED[z])
        if predicted:
            note_parts.append("Only a few atoms (or none in bulk) have been made; bulk properties are unknown or only predicted, so they are not listed.")
        if z in MASS_NUMBER:
            note_parts.append("No standard atomic weight: the value shown is the mass number of the longest-lived known isotope.")
        if PHASE.get(e["phase"]) == "gas":
            note_parts.append("Gas density is given in g/L at 0 °C and 101.325 kPa.")
        if z == 6:
            note_parts.append("Density and melting data refer to graphite; carbon sublimes at atmospheric pressure (about 3900 K).")
            boil = None
        if z in (15, 33):
            boil = boil  # dataset leaves these null (P allotropes, As sublimes)
        if z == 33:
            note_parts.append("Arsenic sublimes at atmospheric pressure (about 887 K).")
        phase = PHASE.get(e["phase"], "solid")
        if predicted:
            phase = "unknown"
        p = e["period"]
        gtext = f", group {group}" if group else ""
        base = f"{e['name']} ({e['symbol']}) is {CAT_TEXT[category]} in period {p}{gtext}."
        if phase != "unknown":
            base += f" It is a {phase} at room temperature."
        if occ == "synthetic":
            base += " It does not occur naturally and is made in particle accelerators or reactors."
        elif occ == "from_decay":
            base += " It is radioactive and occurs naturally only as a short-lived product of uranium and thorium decay."
        elif occ == "trace_natural":
            base += " It is radioactive; only trace amounts occur in nature."
        desc = (NOTES.get(e["symbol"], "") + " " + base).strip() if e["symbol"] in NOTES else base
        mass = e["atomic_mass"]
        if z in MASS_NUMBER:
            mass = float(round(mass))
        out.append({
            "z": z, "symbol": e["symbol"], "name": e["name"],
            "atomicMass": mass, "massIsMassNumber": z in MASS_NUMBER,
            "category": category, "group": group, "period": p, "block": block_of(z, e["group"]),
            "electronConfiguration": config, "shells": e["shells"],
            "oxidationStates": OX.get(z), "density": density, "meltingPointK": melt, "boilingPointK": boil,
            "phase": phase, "electricalConductivity": elec, "thermalConductivity": therm,
            "electronegativity": e.get("electronegativity_pauling"), "occurrence": occ,
            "discovered": None, "description": desc,
            "dataNote": " ".join(note_parts) if note_parts else None,
            "propertiesPredicted": predicted,
        })
    assert len(out) == 118
    OUT.write_text(json.dumps(out, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    print("wrote", OUT, len(out))


if __name__ == "__main__":
    main(sys.argv[1])
