package com.github.realms42.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

public final class FileCopyUtil {

    private FileCopyUtil() {
    }

    /**
     * Copy every regular file under {@code source}, placing each one where
     * {@code destination} maps it. Missing parent directories are created and
     * existing files are replaced.
     *
     * @return the destinations written, in walk order.
     */
    public static List<Path> copyFiles(Path source, Function<Path, Path> destination) throws IOException {
        List<Path> copied = new ArrayList<>();
        try (Stream<Path> files = Files.walk(source)) {
            for (Path file : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
                Path target = destination.apply(file);
                Path parent = target.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
                copied.add(target);
            }
        }
        return copied;
    }
}
