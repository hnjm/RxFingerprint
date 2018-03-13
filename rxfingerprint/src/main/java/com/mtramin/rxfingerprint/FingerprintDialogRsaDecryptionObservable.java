/*
 * Copyright 2018 Marvin Ramin.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * <http://www.apache.org/licenses/LICENSE-2.0>
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mtramin.rxfingerprint;

import android.annotation.SuppressLint;
import android.content.Context;
import android.hardware.fingerprint.FingerprintDialog;
import android.support.annotation.Nullable;

import com.mtramin.rxfingerprint.data.FingerprintDecryptionResult;
import com.mtramin.rxfingerprint.data.FingerprintEncryptionResult;
import com.mtramin.rxfingerprint.data.FingerprintResult;

import javax.crypto.Cipher;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;

@SuppressLint("NewApi")
		// SDK check happens in {@link FingerprintManagerObservable#subscribe}
class FingerprintDialogRsaDecryptionObservable extends FingerprintDialogObservable<FingerprintDecryptionResult> {

	private final RsaCipherProvider cipherProvider;
	private final String encryptedString;
	private final EncodingProvider encodingProvider;
	private final RxFingerprintLogger logger;

	/**
	 * Creates a new AesEncryptionObservable that will listen to fingerprint authentication
	 * to encrypt the given data.
	 *
	 * @param context   context to use
	 * @param keyName   keyName to use for the decryption
	 * @param encrypted data to encrypt  @return Observable {@link FingerprintEncryptionResult}
	 * @return Observable result of the decryption
	 */
	static Observable<FingerprintDecryptionResult> create(Context context,
														  String keyName,
														  String encrypted,
														  boolean keyInvalidatedByBiometricEnrollment,
														  RxFingerprintLogger logger) {
		try {
			return Observable.create(new FingerprintDialogRsaDecryptionObservable(
					new FingerprintApiWrapper(context, logger),
					new RsaCipherProvider(context, keyName, keyInvalidatedByBiometricEnrollment, logger),
					encrypted,
					new Base64Provider(),
					logger));
		} catch (Exception e) {
			return Observable.error(e);
		}
	}

	private FingerprintDialogRsaDecryptionObservable(FingerprintApiWrapper fingerprintApiWrapper,
													 RsaCipherProvider cipherProvider,
													 String encrypted,
													 EncodingProvider encodingProvider,
													 RxFingerprintLogger logger) {
		super(fingerprintApiWrapper);
		this.cipherProvider = cipherProvider;
		encryptedString = encrypted;
		this.encodingProvider = encodingProvider;
		this.logger = logger;
	}

	@Nullable
	@Override
	protected FingerprintDialog.CryptoObject initCryptoObject(ObservableEmitter<FingerprintDecryptionResult> subscriber) {
		try {
			Cipher cipher = cipherProvider.getCipherForDecryption();
			return new FingerprintDialog.CryptoObject(cipher);
		} catch (Exception e) {
			subscriber.onError(e);
			return null;
		}
	}

	@Override
	protected void onAuthenticationSucceeded(ObservableEmitter<FingerprintDecryptionResult> emitter, FingerprintDialog.AuthenticationResult result) {
		try {
			Cipher cipher = result.getCryptoObject().getCipher();
			byte[] bytes = cipher.doFinal(encodingProvider.decode(encryptedString));

			emitter.onNext(new FingerprintDecryptionResult(FingerprintResult.AUTHENTICATED, null, ConversionUtils.toChars(bytes)));
			emitter.onComplete();
		} catch (Exception e) {
			logger.error("Unable to decrypt given value. RxFingerprint is only able to decrypt values previously encrypted by RxFingerprint with the same encryption mode.", e);
			emitter.onError(cipherProvider.mapCipherFinalOperationException(e));
		}

	}

	@Override
	protected void onAuthenticationHelp(ObservableEmitter<FingerprintDecryptionResult> emitter, int helpMessageId, String helpString) {
		emitter.onNext(new FingerprintDecryptionResult(FingerprintResult.HELP, helpString, null));
	}

	@Override
	protected void onAuthenticationFailed(ObservableEmitter<FingerprintDecryptionResult> emitter) {
		emitter.onNext(new FingerprintDecryptionResult(FingerprintResult.FAILED, null, null));
	}
}