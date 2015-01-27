package org.apache.taverna.robundle.utils;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */


import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.CopyOption;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

public class RecursiveCopyFileVisitor extends SimpleFileVisitor<Path> {

	public enum RecursiveCopyOption implements CopyOption {
		/**
		 * Ignore any errors, copy as much as possible. The default is to stop
		 * on the first IOException.
		 * 
		 */
		IGNORE_ERRORS,
	}

	public static void copyRecursively(final Path source,
			final Path destination, final CopyOption... copyOptions)
			throws IOException {
		final Set<CopyOption> copyOptionsSet = new HashSet<>(
				Arrays.asList(copyOptions));

		if (!Files.isDirectory(source)) {
			throw new FileNotFoundException("Not a directory: " + source);
		}
		if (Files.isDirectory(destination)
				&& !copyOptionsSet
						.contains(StandardCopyOption.REPLACE_EXISTING)) {
			throw new FileAlreadyExistsException(destination.toString());
		}
		Path destinationParent = destination.getParent();
		if (destinationParent != null && !Files.isDirectory(destinationParent)) {
			throw new FileNotFoundException("Not a directory: "
					+ destinationParent);
		}

		RecursiveCopyFileVisitor visitor = new RecursiveCopyFileVisitor(
				destination, copyOptionsSet, source);
		Set<FileVisitOption> walkOptions = EnumSet
				.noneOf(FileVisitOption.class);
		if (!copyOptionsSet.contains(LinkOption.NOFOLLOW_LINKS)) {
			walkOptions = EnumSet.of(FileVisitOption.FOLLOW_LINKS);
		}
		Files.walkFileTree(source, walkOptions, Integer.MAX_VALUE, visitor);
	}

	private final CopyOption[] copyOptions;
	private final Set<CopyOption> copyOptionsSet;
	private final Path destination;
	private boolean ignoreErrors;
	private final LinkOption[] linkOptions;
	private final Path source;

	RecursiveCopyFileVisitor(Path destination, Set<CopyOption> copyOptionsSet,
			Path source) {
		this.destination = destination;
		this.source = source;
		this.copyOptionsSet = new HashSet<CopyOption>(copyOptionsSet);

		HashSet<Object> linkOptionsSet = new HashSet<>();
		for (CopyOption option : copyOptionsSet) {
			copyOptionsSet.add(option);
			if (option instanceof LinkOption) {
				linkOptionsSet.add((LinkOption) option);
			}
		}
		this.linkOptions = linkOptionsSet
				.toArray(new LinkOption[(linkOptionsSet.size())]);

		this.ignoreErrors = copyOptionsSet
				.contains(RecursiveCopyOption.IGNORE_ERRORS);

		// To avoid UnsupporteOperationException from native java.nio operations
		// we strip our own options out
		copyOptionsSet.removeAll(EnumSet.allOf(RecursiveCopyOption.class));
		copyOptions = copyOptionsSet.toArray(new CopyOption[(copyOptionsSet
				.size())]);
	}

	private URI pathOnly(URI uri) {
		if (!uri.isAbsolute()) {
			return uri;
		}
		String path = uri.getRawPath();
		// if (! uri.isOpaque()) {
		// path = uri.getPath();
		// }
		if (uri.getScheme().equals("jar")) {
			String part = uri.getSchemeSpecificPart();
			int slashPos = part.indexOf("!/");
			path = part.substring(slashPos + 1, part.length());
		}
		if (path == null) {
			throw new IllegalArgumentException("Can't extract path from URI "
					+ uri);
		}

		if (!path.startsWith("/")) {
			path = "/" + path;
		}
		try {
			return new URI(null, null, path, null);
		} catch (URISyntaxException e) {
			throw new IllegalArgumentException("Can't extract path from URI "
					+ uri, e);
		}

	}

	@Override
	public FileVisitResult postVisitDirectory(Path dir, IOException exc)
			throws IOException {
		try {
			if (copyOptionsSet.contains(StandardCopyOption.COPY_ATTRIBUTES)) {
				// Copy file times
				// Inspired by
				// java.nio.file.CopyMoveHelper.copyToForeignTarget()
				BasicFileAttributes attrs = Files.readAttributes(dir,
						BasicFileAttributes.class, linkOptions);
				BasicFileAttributeView view = Files.getFileAttributeView(
						toDestination(dir), BasicFileAttributeView.class,
						linkOptions);
				view.setTimes(attrs.lastModifiedTime(), attrs.lastAccessTime(),
						attrs.creationTime());
			}
			return FileVisitResult.CONTINUE;
		} catch (IOException ex) {
			return visitFileFailed(dir, ex);
		}
	}

	@Override
	public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
			throws IOException {
		try {
			Path destinationDir = toDestination(dir);
			if (copyOptionsSet.contains(StandardCopyOption.REPLACE_EXISTING)
					&& Files.isDirectory(destinationDir)) {
				return FileVisitResult.CONTINUE;
			}
			Files.copy(dir, destinationDir, copyOptions);
			// Files.createDirectory(destinationDir);
			// System.out.println("Created " + destinationDir + " " +
			// destinationDir.toUri());
			return FileVisitResult.CONTINUE;
		} catch (IOException ex) {
			// Eat or rethrow depending on IGNORE_ERRORS
			return visitFileFailed(dir, ex);
		}
	}

	private Path toDestination(Path path) {
		if (path.equals(source)) {
			// Top-level folder
			return destination;
		}
		// Path relativize = source.relativize(path);
		// return destination.resolve(relativize);
		// The above does not work as ZipPath throws ProviderMisMatchException
		// when given a relative filesystem Path

		URI rel = pathOnly(uriWithSlash(source)).relativize(
				pathOnly(path.toUri()));
		if (rel.isAbsolute()) {
			throw new IllegalStateException("Can't relativize " + rel);
		}
		URI dest = uriWithSlash(destination).resolve(rel);
		return destination.getFileSystem().provider().getPath(dest);
	}

	private URI uriWithSlash(Path dir) {
		URI uri = dir.toUri();
		if (!uri.toString().endsWith("/")) {
			return URI.create(uri.toString() + "/");
		}
		return uri;
	}

	@Override
	public FileVisitResult visitFile(final Path file, BasicFileAttributes attrs)
			throws IOException {
		try {
			Files.copy(file, toDestination(file), copyOptions);
			return FileVisitResult.CONTINUE;
		} catch (IOException ex) {
			return visitFileFailed(file, ex);
		}
	}

	@Override
	public FileVisitResult visitFileFailed(Path file, IOException exc)
			throws IOException {
		if (ignoreErrors) {
			return FileVisitResult.SKIP_SUBTREE;
		}
		// Or - throw exception
		return super.visitFileFailed(file, exc);
	}
}
