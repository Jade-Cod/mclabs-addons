package dev.jade.labsaddons.gametest;

import dev.jade.labsaddons.LabsAddonsClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * The size of every collection the mod holds statically — directly, or one level down inside
 * a static singleton, which is where most of this mod's state lives. Anything that grows
 * without bound across a session has to be reachable from one of these.
 */
final class StaticSizes {
	private static final String ROOT = "dev/jade/labsaddons/";

	private StaticSizes() {
	}

	static Map<String, Integer> snapshot() {
		Map<String, Integer> sizes = new TreeMap<>();
		for (Class<?> type : modClasses()) {
			for (Field field : type.getDeclaredFields()) {
				if (!Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) {
					continue;
				}
				Object value = read(field, null);
				String name = type.getSimpleName() + "." + field.getName();
				record(sizes, name, value);
				if (value != null && isOurs(value.getClass()) && !value.getClass().isEnum()) {
					for (Field inner : value.getClass().getDeclaredFields()) {
						if (!Modifier.isStatic(inner.getModifiers())) {
							record(sizes, name + "." + inner.getName(), read(inner, value));
						}
					}
				}
			}
		}
		return sizes;
	}

	private static void record(Map<String, Integer> sizes, String name, Object value) {
		int size = sizeOf(value);
		if (size >= 0) {
			sizes.put(name, size);
		}
	}

	private static int sizeOf(Object value) {
		if (value instanceof Collection<?> collection) {
			return collection.size();
		}
		if (value instanceof Map<?, ?> map) {
			return map.size();
		}
		if (value instanceof CharSequence text) {
			return text.length();
		}
		return -1;
	}

	private static Object read(Field field, Object owner) {
		try {
			field.setAccessible(true);
			return field.get(owner);
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			return null;
		}
	}

	private static boolean isOurs(Class<?> type) {
		return type.getName().startsWith("dev.jade.labsaddons.") && !type.getName().contains(".gametest.");
	}

	/** Every class in the mod, bar the mixins, which can't be loaded as ordinary classes. */
	private static java.util.List<Class<?>> modClasses() {
		// The class files' own location: in a dev run the mod's root path is only its resources.
		Path root;
		try {
			root = Path.of(LabsAddonsClient.class.getProtectionDomain().getCodeSource().getLocation().toURI());
		} catch (java.net.URISyntaxException e) {
			throw new IllegalStateException(e);
		}
		try (Stream<Path> files = Files.walk(root.resolve(ROOT))) {
			return files.map(p -> root.relativize(p).toString())
					.filter(p -> p.endsWith(".class") && !p.contains("/mixin/"))
					.map(p -> p.substring(0, p.length() - ".class".length()).replace('/', '.'))
					.map(StaticSizes::load)
					.filter(java.util.Objects::nonNull)
					.toList();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static Class<?> load(String name) {
		try {
			return Class.forName(name, false, StaticSizes.class.getClassLoader());
		} catch (ClassNotFoundException | LinkageError e) {
			return null;
		}
	}
}
