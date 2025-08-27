package com.ash.simpledataentry.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountEncryption @Inject constructor() {
    
    private val keyAlias = "DHIS2_ACCOUNT_KEY"
    private val transformation = "AES/GCM/NoPadding"
    private val androidKeyStore = "AndroidKeyStore"
    
    init {
        generateKey()
    }
    
    private fun generateKey() {
        try {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, androidKeyStore)
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(false) // Set to true if you want biometric/PIN protection
                .build()
            
            keyGenerator.init(keyGenParameterSpec)
            keyGenerator.generateKey()
        } catch (e: Exception) {
            Log.e("AccountEncryption", "Failed to generate encryption key", e)
        }
    }
    
    private fun getSecretKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(androidKeyStore)
            keyStore.load(null)
            keyStore.getKey(keyAlias, null) as SecretKey
        } catch (e: Exception) {
            Log.e("AccountEncryption", "Failed to get secret key", e)
            null
        }
    }
    
    fun encryptPassword(password: String): String? {
        return try {
            val secretKey = getSecretKey() ?: return null
            val cipher = Cipher.getInstance(transformation)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            
            val iv = cipher.iv
            val encryptedPassword = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
            
            // Combine IV and encrypted data
            val combined = iv + encryptedPassword
            Base64.encodeToString(combined, Base64.DEFAULT)
        } catch (e: Exception) {
            Log.e("AccountEncryption", "Failed to encrypt password", e)
            null
        }
    }
    
    fun decryptPassword(encryptedPassword: String): String? {
        return try {
            val secretKey = getSecretKey() ?: return null
            val combined = Base64.decode(encryptedPassword, Base64.DEFAULT)
            
            // Extract IV (first 12 bytes for GCM)
            val iv = combined.sliceArray(0..11)
            val encryptedData = combined.sliceArray(12 until combined.size)
            
            val cipher = Cipher.getInstance(transformation)
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            
            val decryptedData = cipher.doFinal(encryptedData)
            String(decryptedData, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e("AccountEncryption", "Failed to decrypt password", e)
            null
        }
    }
    
    fun isEncryptionAvailable(): Boolean {
        return try {
            val secretKey = getSecretKey()
            secretKey != null
        } catch (e: Exception) {
            Log.e("AccountEncryption", "Encryption not available", e)
            false
        }
    }
    
    fun deleteKey() {
        try {
            val keyStore = KeyStore.getInstance(androidKeyStore)
            keyStore.load(null)
            keyStore.deleteEntry(keyAlias)
        } catch (e: Exception) {
            Log.e("AccountEncryption", "Failed to delete encryption key", e)
        }
    }
}