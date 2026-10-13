package com.scientia.core;

import com.scientia.core.data.ScienceDatabase;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Loads the real mod data files once for all tests. */
public final class TestData {
    private static ScienceDatabase db;

    private TestData() {
    }

    public static synchronized ScienceDatabase db() {
        if (db == null) {
            String dir = System.getProperty("scientia.dataDir", "../src/main/resources/data/scientia/science");
            try {
                db = ScienceDatabase.load(file -> Files.newBufferedReader(Path.of(dir, file), StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return db;
    }
}
