package org.apache.cloudberry.pxf.automation.testcontainers;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 *
 */
public class ClasspathDockerContainerBuilder {

    private ClasspathDockerContainerBuilder() {
    }

    /**
     * Builds named Docker image from resources in the classpath.
     *
     * @param imageName - name of the image to build
     * @param resourceDirectory - resource path to Dockerfile's folder
     * @param resources - list of files to copy (relative to resourceDirectory)
     */
    public static void ensureImageExists(String imageName, String resourceDirectory, String[] resources) {
        if (imageExists(imageName)) {
            System.out.println("=== Image '" + imageName + "' already exists locally, skip build ===");
            return;
        }
        try {
            Path contextDir = Files.createTempDirectory("tc-docker-context-");
            for (String resource : resources) {
                Path target = contextDir.resolve(resource);
                Files.createDirectories(target.getParent());
                try (InputStream is = ClasspathDockerContainerBuilder.class
                        .getClassLoader()
                        .getResourceAsStream(resourceDirectory + "/" + resource)) {
                    if (is == null) {
                        throw new IllegalStateException("Classpath resource not found: " + resource);
                    }
                    Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            dockerBuild(contextDir.toFile(), imageName);
        } catch (IOException e) {
            throw new RuntimeException("Failed to prepare Docker build context from classpath", e);
        }
    }

    private static boolean imageExists(String imageName) {
        try {
            Process process = new ProcessBuilder("docker", "image", "inspect", imageName)
                    .redirectErrorStream(true)
                    .start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to check Docker image existence: " + imageName, e);
        }
    }

    private static void dockerBuild(File contextDir, String tag) {
        System.out.println("=== docker build -t " + tag + " " + contextDir + " ===");
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "docker", "build",
                    "-t", tag, ".")
                    .directory(contextDir)
                    .redirectErrorStream(true);
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException(
                        "docker build failed for '" + tag + "' (exit " + exitCode + "). "
                                + "Context dir: " + contextDir.getAbsolutePath());
            }
            System.out.println("=== Image '" + tag + "' built successfully ===");
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to build Docker image '" + tag + "'", e);
        }
    }
}
