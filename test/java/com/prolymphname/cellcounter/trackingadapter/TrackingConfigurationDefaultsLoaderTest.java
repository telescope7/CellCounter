package com.prolymphname.cellcounter.trackingadapter;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class TrackingConfigurationDefaultsLoaderTest {

    @Test
    public void resolveRequiredDefaultConfigPath_returnsRepositoryPropertiesFile() {
        TrackingConfigurationDefaultsLoader.clearCacheForTests();

        Path path = TrackingConfigurationDefaultsLoader.resolveRequiredDefaultConfigPath();

        assertNotNull(path);
        assertTrue(Files.isRegularFile(path));
        assertEquals(TrackingConfigurationDefaultsLoader.DEFAULT_CONFIG_FILE_NAME, path.getFileName().toString());
    }

    @Test
    public void loadRequiredDefaults_populatesCachedDefaultsPath() {
        TrackingConfigurationDefaultsLoader.clearCacheForTests();

        TrackingConfiguration defaults = TrackingConfigurationDefaultsLoader.loadRequiredDefaults();
        Path cachedPath = TrackingConfigurationDefaultsLoader.getCachedDefaultsPath();

        assertNotNull(defaults);
        assertNotNull(cachedPath);
        assertTrue(Files.isRegularFile(cachedPath));
        assertEquals(TrackingConfigurationDefaultsLoader.DEFAULT_CONFIG_FILE_NAME, cachedPath.getFileName().toString());
    }
}
