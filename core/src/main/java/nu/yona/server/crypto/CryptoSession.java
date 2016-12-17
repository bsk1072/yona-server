/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.crypto;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.InvalidParameterSpecException;
import java.security.spec.KeySpec;
import java.util.Arrays;
import java.util.Optional;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nu.yona.server.exceptions.YonaException;

public class CryptoSession implements AutoCloseable
{
	private static final int CRYPTO_VARIANT_NUMBER_LENGTH = 1;
	private static final byte CURRENT_CRYPTO_VARIANT_NUMBER = 1;
	private static final String CIPHER_TYPE = "AES/CBC/PKCS5Padding";

	public interface Executable<T>
	{
		T execute();
	}

	public interface VoidPredicate
	{
		boolean test();
	}

	private static final Logger logger = LoggerFactory.getLogger(CryptoSession.class);
	private static final byte[] SALT = "0123456789012345".getBytes();
	private static final int INITIALIZATION_VECTOR_LENGTH = 16;
	private static ThreadLocal<CryptoSession> threadLocal = new ThreadLocal<>();
	private Cipher encryptionCipher;
	private Optional<byte[]> initializationVector = Optional.empty();
	private final SecretKey secretKey;
	private final CryptoSession previousCryptoSession;
	private Cipher decryptionCipher;

	private CryptoSession(String password, CryptoSession previousCryptoSession)
	{
		secretKey = getSecretKey(password.toCharArray());
		this.previousCryptoSession = previousCryptoSession;
		threadLocal.set(this);
	}

	@Override
	public void close()
	{
		logger.debug("Closing crypto session on thread {}", Thread.currentThread());
		threadLocal.set(previousCryptoSession);
	}

	public static <T> T execute(Optional<String> password, Executable<T> executable)
	{
		return execute(password, null, executable);
	}

	public static void execute(Optional<String> password, Runnable runnable)
	{
		execute(password, null, runnable);
	}

	public static <T> T execute(Optional<String> password, VoidPredicate passwordChecker, Executable<T> executable)
	{
		try (CryptoSession cryptoSession = CryptoSession.start(getPassword(password)))
		{
			if (passwordChecker != null && !passwordChecker.test())
			{
				throw CryptoException.decryptingData();
			}
			return executable.execute();
		}
	}

	public static void execute(Optional<String> password, VoidPredicate passwordChecker, Runnable runnable)
	{
		execute(password, passwordChecker, () -> {
			runnable.run();
			return Boolean.TRUE;
		});
	}

	private Cipher getEncryptionCipher()
	{
		try
		{
			if (encryptionCipher == null)
			{
				encryptionCipher = Cipher.getInstance(CIPHER_TYPE);
				if (!isInitializationVectorSet())
				{
					encryptionCipher.init(Cipher.ENCRYPT_MODE, secretKey);
				}
				else
				{
					encryptionCipher.init(Cipher.ENCRYPT_MODE, secretKey, new IvParameterSpec(initializationVector.get()));
				}
			}
			return encryptionCipher;
		}
		catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | InvalidAlgorithmParameterException e)
		{
			throw CryptoException.gettingCipher(CIPHER_TYPE);
		}
	}

	private static CryptoSession start(String password)
	{
		logger.debug("Starting crypto session on thread {}", Thread.currentThread());
		return new CryptoSession(password, threadLocal.get());
	}

	public static CryptoSession getCurrent()
	{
		CryptoSession cryptoSession = threadLocal.get();
		if (cryptoSession == null)
		{
			throw CryptoException.noActiveCryptoSession(Thread.currentThread());
		}
		return cryptoSession;
	}

	/**
	 * Encrypts the given plaintext bytes.
	 * 
	 * @param plaintext the bytes to be encrypted
	 * @return the encrypted bytes, with the crypto variant number prepended to it.
	 */
	public byte[] encrypt(byte[] plaintext)
	{
		try
		{
			byte[] ciphertext = new byte[getEncryptionCipher().getOutputSize(plaintext.length) + CRYPTO_VARIANT_NUMBER_LENGTH];
			setCryptoVariantNumber(ciphertext);
			int bytesStored = getEncryptionCipher().doFinal(plaintext, 0, plaintext.length, ciphertext, 1);
			if (bytesStored != ciphertext.length - CRYPTO_VARIANT_NUMBER_LENGTH)
			{
				ciphertext = Arrays.copyOf(ciphertext, ciphertext.length + CRYPTO_VARIANT_NUMBER_LENGTH);
			}
			return ciphertext;
		}
		catch (IllegalBlockSizeException | BadPaddingException | ShortBufferException e)
		{
			throw CryptoException.encryptingData(e);
		}
	}

	/**
	 * Decrypts the given ciphertext bytes.
	 * 
	 * @param ciphertext the bytes to be decrypted, with a leading crypto variant number
	 * @return the decrypted bytes
	 */
	public byte[] decrypt(byte[] ciphertext)
	{
		try
		{
			verifyCryptoVariantNumber(ciphertext);
			byte[] plaintext = new byte[getDecryptionCipher().getOutputSize(ciphertext.length)];
			int bytesStored = getDecryptionCipher().doFinal(ciphertext, CRYPTO_VARIANT_NUMBER_LENGTH,
					plaintext.length - CRYPTO_VARIANT_NUMBER_LENGTH, plaintext, 0);
			if (bytesStored != plaintext.length)
			{
				plaintext = Arrays.copyOf(plaintext, bytesStored);
			}
			return plaintext;
		}
		catch (IllegalBlockSizeException | BadPaddingException | ShortBufferException e)
		{
			throw CryptoException.decryptingData(e);
		}
	}

	private void setCryptoVariantNumber(byte[] ciphertext)
	{
		ciphertext[0] = CURRENT_CRYPTO_VARIANT_NUMBER;
	}

	private void verifyCryptoVariantNumber(byte[] ciphertext)
	{
		if (ciphertext[0] != CURRENT_CRYPTO_VARIANT_NUMBER)
		{
			throw CryptoException.decryptingData();
		}
	}

	private SecretKey getSecretKey(char[] password)
	{
		try
		{
			SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
			KeySpec spec = new PBEKeySpec(password, SALT, 65536, 128);
			SecretKey tmp = factory.generateSecret(spec);
			return new SecretKeySpec(tmp.getEncoded(), "AES");
		}
		catch (NoSuchAlgorithmException | InvalidKeySpecException e)
		{
			throw CryptoException.creatingSecretKey(e);
		}
	}

	public byte[] generateInitializationVector()
	{
		try
		{
			byte[] newInitializationVector = getEncryptionCipher().getParameters().getParameterSpec(IvParameterSpec.class)
					.getIV();
			setInitializationVector(newInitializationVector);
			return getInitializationVector();
		}
		catch (InvalidParameterSpecException e)
		{
			throw YonaException.unexpected(e);
		}
	}

	private byte[] getInitializationVector()
	{
		if (!isInitializationVectorSet())
		{
			throw CryptoException.initializationVectorNotSet();
		}

		return initializationVector.get();
	}

	public void setInitializationVector(byte[] initializationVector)
	{
		if (initializationVector == null)
		{
			throw CryptoException.initializationVectorParameterNull();
		}
		if (initializationVector.length != INITIALIZATION_VECTOR_LENGTH)
		{
			throw CryptoException.initializationVectorWrongSize(initializationVector.length, INITIALIZATION_VECTOR_LENGTH);
		}
		if (isInitializationVectorSet())
		{
			if (Arrays.equals(initializationVector, getInitializationVector()))
			{
				return;
			}
			throw CryptoException.initializationVectorOverwrite();
		}

		this.initializationVector = Optional.of(initializationVector);
	}

	private boolean isInitializationVectorSet()
	{
		return initializationVector.isPresent();
	}

	private Cipher getDecryptionCipher()
	{
		try
		{
			if (decryptionCipher == null)
			{
				if (!isInitializationVectorSet())
				{
					throw CryptoException.initializationVectorNotSet();
				}
				decryptionCipher = Cipher.getInstance(CIPHER_TYPE);
				decryptionCipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(getInitializationVector()));
			}
			return decryptionCipher;
		}
		catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | InvalidAlgorithmParameterException e)
		{
			throw CryptoException.gettingCipher(CIPHER_TYPE);
		}
	}

	private static String getPassword(Optional<String> password)
	{
		return password.orElseThrow(() -> MissingPasswordException.passwordHeaderNotProvided());
	}
}
