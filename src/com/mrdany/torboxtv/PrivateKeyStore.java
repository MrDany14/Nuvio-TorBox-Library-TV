package com.mrdany.torboxtv;
import android.content.Context;import android.content.SharedPreferences;import android.security.keystore.KeyGenParameterSpec;import android.security.keystore.KeyProperties;import android.util.Base64;import java.security.KeyStore;import javax.crypto.Cipher;import javax.crypto.KeyGenerator;import javax.crypto.SecretKey;import javax.crypto.spec.GCMParameterSpec;
public final class PrivateKeyStore {
 private static final String ALIAS="mrdany.torbox.library.tv.v1";private final SharedPreferences prefs;
 public PrivateKeyStore(Context c){prefs=c.getSharedPreferences("mrdany_torbox_library_private",Context.MODE_PRIVATE);}
 private SecretKey key()throws Exception{KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);if(ks.containsAlias(ALIAS))return ((KeyStore.SecretKeyEntry)ks.getEntry(ALIAS,null)).getSecretKey();KeyGenerator g=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");g.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());return g.generateKey();}
 public void save(String value)throws Exception{if(value.trim().isEmpty())throw new IllegalArgumentException("Enter a key");Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());String encoded=Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(cipher.doFinal(value.trim().getBytes("UTF-8")),Base64.NO_WRAP);if(!prefs.edit().putString("encrypted_key",encoded).commit())throw new Exception("Storage failed");}
 public String read()throws Exception{String s=prefs.getString("encrypted_key","");if(s.isEmpty())return "";String[] p=s.split(":",2);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(p[0],Base64.NO_WRAP)));return new String(c.doFinal(Base64.decode(p[1],Base64.NO_WRAP)),"UTF-8");}
 public String encryptedValue(){return prefs.getString("encrypted_key","");}
 public void clear(){prefs.edit().remove("encrypted_key").commit();}
}

