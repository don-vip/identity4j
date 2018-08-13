package com.identity4j.util.unix;

import java.security.NoSuchAlgorithmException;

import org.junit.Assert;
import org.junit.Test;

/*  From here - https://github.com/pyca/bcrypt/blob/master/tests/test_bcrypt.py */
public class BCryptKDFTest {

	@Test
	public void test4Rounds1() throws NoSuchAlgorithmException {
		byte[] expected = new byte[] { 0x5b, (byte) 0xbf, 0x0c, (byte) 0xc2, (byte) 0x93, 0x58, 0x7f, 0x1c, 0x36, 0x35,
				0x55, 0x5c, 0x27, 0x79, 0x65, (byte) 0x98, (byte) 0xd4, 0x7e, 0x57, (byte) 0x90, 0x71, (byte) 0xbf,
				0x42, 0x7e, (byte) 0x9d, (byte) 0x8f, (byte) 0xbe, (byte) 0x84, 0x2a, (byte) 0xba, 0x34, (byte) 0xd9 };
		byte[] key = BCryptKDF.bcrypt_pbkdf("password".getBytes(), "salt".getBytes(), expected.length, 4);
		Assert.assertArrayEquals(expected, key);

	}

	@Test
	public void test4RoundsEmptySalt() throws NoSuchAlgorithmException {
		byte[] expected = new byte[] { (byte) 0xc1, 0x2b, 0x56, 0x62, 0x35, (byte) 0xee, (byte) 0xe0, 0x4c, 0x21, 0x25,
				(byte) 0x98, (byte) 0x97, 0x0a, 0x57, (byte) 0x9a, 0x67 };
		byte[] key = BCryptKDF.bcrypt_pbkdf("password".getBytes(), new byte[] { 0 }, expected.length, 4);
		Assert.assertArrayEquals(expected, key);
	}

	@Test
	public void test4RoundsEmptyPassword() throws NoSuchAlgorithmException {
		byte[] expected = new byte[] { 0x60, 0x51, (byte) 0xbe, 0x18, (byte) 0xc2, (byte) 0xf4, (byte) 0xf8, 0x2c,
				(byte) 0xbf, 0x0e, (byte) 0xfe, (byte) 0xe5, 0x47, 0x1b, 0x4b, (byte) 0xb9 };
		byte[] key = BCryptKDF.bcrypt_pbkdf(new byte[] { 0 }, "salt".getBytes(), expected.length, 4);
		Assert.assertArrayEquals(expected, key);
	}

	@Test
	public void test4RoundsNulPasswordAndSalt() throws NoSuchAlgorithmException {
		byte[] expected = new byte[] { 0x74, 0x10, (byte) 0xe4, 0x4c, (byte) 0xf4, (byte) 0xfa, 0x07, (byte) 0xbf,
				(byte) 0xaa, (byte) 0xc8, (byte) 0xa9, 0x28, (byte) 0xb1, 0x72, 0x7f, (byte) 0xac, 0x00, 0x13, 0x75,
				(byte) 0xe7, (byte) 0xbf, 0x73, (byte) 0x84, 0x37, 0x0f, 0x48, (byte) 0xef, (byte) 0xd1, 0x21, 0x74,
				0x30, 0x50 };
		byte[] key = BCryptKDF.bcrypt_pbkdf("password\u0000".getBytes(), "salt\u0000".getBytes(), expected.length, 4);
		Assert.assertArrayEquals(expected, key);
	}

	@Test
	public void test4RoundsNulPasswordAndSalt2() throws NoSuchAlgorithmException {
		byte[] expected = new byte[] { (byte) 0xc2, (byte) 0xbf, (byte) 0xfd, (byte) 0x9d, (byte) 0xb3, (byte) 0x8f,
				0x65, 0x69, (byte) 0xef, (byte) 0xef, 0x43, 0x72, (byte) 0xf4, (byte) 0xde, (byte) 0x83, (byte) 0xc0 };
		byte[] key = BCryptKDF.bcrypt_pbkdf("pass\u0000wor".getBytes(), "sa\u0000l".getBytes(), expected.length, 4);
		Assert.assertArrayEquals(expected, key);
	}

	@Test
	public void test4RoundsNulPasswordAndSalt3() throws NoSuchAlgorithmException {
		byte[] expected = new byte[] { 0x4b, (byte) 0xa4, (byte) 0xac, 0x39, 0x25, (byte) 0xc0, (byte) 0xe8,
				(byte) 0xd7, (byte) 0xf0, (byte) 0xcd, (byte) 0xb6, (byte) 0xbb, 0x16, (byte) 0x84, (byte) 0xa5, 0x6f };
		byte[] key = BCryptKDF.bcrypt_pbkdf("pass\u0000word".getBytes(), "sa\u0000lt".getBytes(), expected.length, 4);
		Assert.assertArrayEquals(expected, key);
	}

	@Test
	public void test8RoundsBiggerKey() throws NoSuchAlgorithmException {
		byte[] expected = new byte[] { (byte) 0xe1, 0x36, 0x7e, (byte) 0xc5, 0x15, 0x1a, 0x33, (byte) 0xfa, (byte) 0xac,
				0x4c, (byte) 0xc1, (byte) 0xc1, 0x44, (byte) 0xcd, 0x23, (byte) 0xfa, 0x15, (byte) 0xd5, 0x54,
				(byte) 0x84, (byte) 0x93, (byte) 0xec, (byte) 0xc9, (byte) 0x9b, (byte) 0x9b, 0x5d, (byte) 0x9c, 0x0d,
				0x3b, 0x27, (byte) 0xbe, (byte) 0xc7, 0x62, 0x27, (byte) 0xea, 0x66, 0x08, (byte) 0x8b, (byte) 0x84,
				(byte) 0x9b, 0x20, (byte) 0xab, 0x7a, (byte) 0xa4, 0x78, 0x01, 0x02, 0x46, (byte) 0xe7, 0x4b,
				(byte) 0xba, 0x51, 0x72, 0x3f, (byte) 0xef, (byte) 0xa9, (byte) 0xf9, 0x47, 0x4d, 0x65, 0x08,
				(byte) 0x84, 0x5e, (byte) 0x8d };
		byte[] key = BCryptKDF.bcrypt_pbkdf("password".getBytes(), "salt".getBytes(), expected.length, 8);
		Assert.assertArrayEquals(expected, key);
	}

	@Test
	public void test42Rounds() throws NoSuchAlgorithmException {
		byte[] expected = new byte[] { (byte) 0x83, 0x3c, (byte) 0xf0, (byte) 0xdc, (byte) 0xf5, 0x6d, (byte) 0xb6,
				0x56, 0x08, (byte) 0xe8, (byte) 0xf0, (byte) 0xdc, 0x0c, (byte) 0xe8, (byte) 0x82, (byte) 0xbd };
		byte[] key = BCryptKDF.bcrypt_pbkdf("password".getBytes(), "salt".getBytes(), expected.length, 42);
		Assert.assertArrayEquals(expected, key);
	}

	@Test
	public void testLongerPassword() throws Exception {
		byte[] expected = new byte[] { 0x10, (byte) 0x97, (byte) 0x8b, 0x07, 0x25, 0x3d, (byte) 0xf5, 0x7f, 0x71,
				(byte) 0xa1, 0x62, (byte) 0xeb, 0x0e, (byte) 0x8a, (byte) 0xd3, 0x0a };
		byte[] key = BCryptKDF.bcrypt_pbkdf(
				("Lorem ipsum dolor sit amet, consectetur adipisicing elit, sed do "
						+ "eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut "
						+ "enim ad minim veniam, quis nostrud exercitation ullamco laboris "
						+ "nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor "
						+ "in reprehenderit in voluptate velit esse cillum dolore eu fugiat "
						+ "nulla pariatur. Excepteur sint occaecat cupidatat non proident, "
						+ "sunt in culpa qui officia deserunt mollit anim id est laborum.").getBytes("ASCII"),
				"salis\u0000".getBytes("ASCII"), expected.length, 8);
		Assert.assertArrayEquals(expected, key);
	}

	@Test
	public void testUnicode() throws Exception {
		byte[] expected = new byte[] { 0x20, 0x44, 0x38, 0x17, 0x5e, (byte) 0xee, 0x7c, (byte) 0xe1, 0x36, (byte) 0xc9,
				0x1b, 0x49, (byte) 0xa6, 0x79, 0x23, (byte) 0xff };
		byte[] key = BCryptKDF.bcrypt_pbkdf(
				new byte[] { 0x0d, (byte) 0xb3, (byte) 0xac, (byte) 0x94, (byte) 0xb3, (byte) 0xee, 0x53, 0x28, 0x4f,
						0x4a, 0x22, (byte) 0x89, 0x3b, 0x3c, 0x24, (byte) 0xae },
				new byte[] { (byte) 0x3a, 0x62, (byte) 0xf0, (byte) 0xf0, (byte) 0xdb, (byte) 0xce, (byte) 0xf8, 0x23,
						(byte) 0xcf, (byte) 0xcc, (byte) 0x85, 0x48, 0x56, (byte) 0xea, 0x10, 0x28 },
				expected.length, 8);
		Assert.assertArrayEquals(expected, key);
	}
}
