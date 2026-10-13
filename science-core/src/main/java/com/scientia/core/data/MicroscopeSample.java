package com.scientia.core.data;

/** A specimen that can be viewed in the microscope; {@code item} is the Minecraft item placed on the stage. */
public record MicroscopeSample(String id, String item, String title, int magnification, double fieldOfViewUm, String texture,
                               String description, String abstraction) {
}
