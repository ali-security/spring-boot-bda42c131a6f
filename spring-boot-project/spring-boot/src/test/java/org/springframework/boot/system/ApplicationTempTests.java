/*
 * Copyright 2012-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.system;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.util.EnumSet;
import java.util.Set;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.FileSystemUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * Tests for {@link ApplicationTemp}.
 *
 * @author Phillip Webb
 */
public class ApplicationTempTests {

	@Before
	@After
	public void cleanup() {
		FileSystemUtils.deleteRecursively(new ApplicationTemp().getDir());
	}

	@Test
	public void generatesConsistentTemp() {
		ApplicationTemp t1 = new ApplicationTemp();
		ApplicationTemp t2 = new ApplicationTemp();
		assertThat(t1.getDir()).isNotNull();
		assertThat(t1.getDir()).isEqualTo(t2.getDir());
	}

	@Test
	public void differentBasedOnUserDir() {
		String userDir = System.getProperty("user.dir");
		try {
			File t1 = new ApplicationTemp().getDir();
			System.setProperty("user.dir", "abc");
			File t2 = new ApplicationTemp().getDir();
			assertThat(t1).isNotEqualTo(t2);
		}
		finally {
			System.setProperty("user.dir", userDir);
		}
	}

	@Test
	public void getSubDir() {
		ApplicationTemp temp = new ApplicationTemp();
		assertThat(temp.getDir("abc")).isEqualTo(new File(temp.getDir(), "abc"));
	}

	@Test
	public void posixPermissions() throws IOException {
		assumePosixFileSystem();
		ApplicationTemp temp = new ApplicationTemp();
		assertDirectoryPermissions(temp.getDir().toPath());
		assertDirectoryPermissions(temp.getDir("sub").toPath());
	}

	@Test
	public void whenDirectoryExistsWithWrongPermissionsGetDirThrows() throws IOException {
		assumePosixFileSystem();
		ApplicationTemp temp = new ApplicationTemp();
		Path path = temp.getDir().toPath();
		try {
			Files.getFileAttributeView(path, PosixFileAttributeView.class)
					.setPermissions(EnumSet.allOf(PosixFilePermission.class));
			assertThatIllegalStateException().isThrownBy(new ApplicationTemp()::getDir)
					.withMessageContaining("does not have the permissions");
		}
		finally {
			FileSystemUtils.deleteRecursively(path);
		}
	}

	@Test
	public void whenSubDirectoryExistsWithWrongPermissionsGetDirThrows() throws IOException {
		assumePosixFileSystem();
		ApplicationTemp temp = new ApplicationTemp();
		Path path = temp.getDir().toPath();
		try {
			Path subDir = temp.getDir("sub").toPath();
			Files.getFileAttributeView(subDir, PosixFileAttributeView.class)
					.setPermissions(EnumSet.allOf(PosixFilePermission.class));
			assertThatIllegalStateException().isThrownBy(() -> new ApplicationTemp().getDir("sub"))
					.withMessageContaining("does not have the permissions");
		}
		finally {
			FileSystemUtils.deleteRecursively(path);
		}
	}

	@Test
	public void whenDirectoryExistsWithWrongOwnerGetDirThrows() throws IOException {
		assumePosixFileSystem();
		ApplicationTemp temp = new ApplicationTemp();
		Path path = temp.getDir().toPath();
		try {
			ApplicationTemp otherOwner = new ApplicationTemp();
			ReflectionTestUtils.setField(otherOwner, "directoryOwner", new UserPrincipal() {

				@Override
				public String getName() {
					return "other-user";
				}

			});
			assertThatIllegalStateException().isThrownBy(otherOwner::getDir)
					.withMessageContaining("is not owned by other-user");
		}
		finally {
			FileSystemUtils.deleteRecursively(path);
		}
	}

	@Test
	public void whenSymlinkExistsInDirectoryLocationGetDirThrows() throws IOException {
		ApplicationTemp temp = new ApplicationTemp();
		Path path = temp.getDir().toPath();
		FileSystemUtils.deleteRecursively(path);
		Path linkTarget = Files.createTempDirectory("application-test-tests");
		try {
			try {
				Files.createSymbolicLink(path, linkTarget);
			}
			catch (Exception ex) {
				Assume.assumeTrue("Symlink creation not supported", false);
			}
			assertThatIllegalStateException().isThrownBy(new ApplicationTemp()::getDir)
					.withMessageContaining("already exists but it is not a directory");
		}
		finally {
			Files.deleteIfExists(path);
			FileSystemUtils.deleteRecursively(linkTarget);
		}
	}

	@Test
	public void whenFileExistsInDirectoryLocationGetDirThrows() throws IOException {
		ApplicationTemp temp = new ApplicationTemp();
		Path path = temp.getDir().toPath();
		FileSystemUtils.deleteRecursively(path);
		Files.createFile(path);
		try {
			assertThatIllegalStateException().isThrownBy(new ApplicationTemp()::getDir)
					.withMessageContaining("already exists but it is not a directory");
		}
		finally {
			Files.deleteIfExists(path);
		}
	}

	private void assumePosixFileSystem() {
		Assume.assumeTrue("POSIX file attributes not supported",
				FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
	}

	private void assertDirectoryPermissions(Path path) throws IOException {
		Set<PosixFilePermission> permissions = Files.getFileAttributeView(path, PosixFileAttributeView.class)
				.readAttributes().permissions();
		assertThat(permissions).containsExactlyInAnyOrder(PosixFilePermission.OWNER_READ,
				PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE);
	}

}
