package com.github.realms42.mojos;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import org.apache.maven.plugin.logging.SystemStreamLog;

final class MojoTesting {

    private MojoTesting() {
    }

    static <T extends BaseMojo> T configure(T mojo, Path project, String version) {
        mojo.basePath = project.toFile();
        mojo.buildDir = project.resolve("target").toFile();
        mojo.version = version;
        mojo.setLog(new RecordingLog());
        return mojo;
    }

    // Maven injects @Parameter fields reflectively, so tests set the private ones the same way.
    static void set(Object target, String name, Object value) {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                // declared further up the hierarchy
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        throw new IllegalArgumentException("No field '" + name + "' on " + target.getClass());
    }

    static RecordingLog log(BaseMojo mojo) {
        return (RecordingLog) mojo.getLog();
    }

    // Stands in for the processes a mojo starts: records each command and answers with a scripted exit code.
    static final class Commands {
        final List<List<String>> commands = new ArrayList<>();
        final List<Map<String, String>> environments = new ArrayList<>();
        private final Deque<Integer> exitCodes = new ArrayDeque<>();

        Commands exitingWith(Integer... codes) {
            exitCodes.addAll(Arrays.asList(codes));
            return this;
        }

        int exec(String executable, List<String> args, Map<String, String> env) {
            List<String> command = new ArrayList<>();
            command.add(executable);
            command.addAll(args);
            commands.add(command);
            environments.add(env);
            return exitCodes.isEmpty() ? 0 : exitCodes.poll();
        }
    }

    static final class RecordingLog extends SystemStreamLog {
        final List<String> info = new ArrayList<>();
        final List<String> warnings = new ArrayList<>();

        @Override
        public void info(CharSequence content) {
            info.add(content.toString());
        }

        @Override
        public void warn(CharSequence content) {
            warnings.add(content.toString());
        }
    }
}
