package org.hongen.sonylutremote;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Locale;

/** Pairing data is kept in the app's private preferences. The WPA2 passphrase
 * and the pairing token are never logged and never placed in the URL. */
final class SettingsStore {
    private static final String PREFS="sonylut_remote";
    private static final String SSID="ssid",PSK="psk",TOKEN="token",HOST="host",PORT="port",EXISTING="include_existing";
    private final SharedPreferences preferences;

    SettingsStore(Context context){
        preferences=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    }
    String ssid(){return preferences.getString(SSID,"");}
    String psk(){return preferences.getString(PSK,"");}
    String token(){return preferences.getString(TOKEN,"");}
    String host(){return preferences.getString(HOST,"");}
    int port(){return preferences.getInt(PORT,8080);}
    boolean includeExisting(){return preferences.getBoolean(EXISTING,false);}

    void save(String ssid,String psk,String token,String host,String port,boolean includeExisting)throws IllegalArgumentException {
        String cleanSsid=ssid==null?"":ssid.trim();
        String cleanPsk=psk==null?"":psk;
        String cleanToken=normalizeToken(token);
        String cleanHost=normalizeHost(host);
        int cleanPort=parsePort(port);
        if(cleanSsid.length()==0)throw new IllegalArgumentException("请填写相机 Wi-Fi 名称");
        if(cleanPsk.length()<8||cleanPsk.length()>63)throw new IllegalArgumentException("Wi-Fi 密码应为 8–63 个字符");
        if(cleanToken.length()<8||cleanToken.length()>64)throw new IllegalArgumentException("配对码格式不正确");
        if(cleanHost.length()==0)throw new IllegalArgumentException("请填写相机地址");
        preferences.edit()
            .putString(SSID,cleanSsid).putString(PSK,cleanPsk).putString(TOKEN,cleanToken)
            .putString(HOST,cleanHost).putInt(PORT,cleanPort).putBoolean(EXISTING,includeExisting)
            .commit();
    }
    void setIncludeExisting(boolean include){
        preferences.edit().putBoolean(EXISTING,include).commit();
    }
    static String normalizeToken(String token){
        if(token==null)return "";
        StringBuilder out=new StringBuilder();
        for(int i=0;i<token.length();i++){
            char c=token.charAt(i);
            if(c=='-'||c==' '||c=='\t'||c=='\n'||c=='\r')continue;
            out.append(Character.toUpperCase(c));
        }
        return out.toString();
    }
    static String normalizeHost(String host){
        String value=host==null?"":host.trim().toLowerCase(Locale.US);
        if(value.startsWith("http://"))value=value.substring(7);
        if(value.startsWith("https://"))value=value.substring(8);
        int slash=value.indexOf('/');if(slash>=0)value=value.substring(0,slash);
        int colon=value.indexOf(':');
        if(colon>0&&value.indexOf(':')==value.lastIndexOf(':'))value=value.substring(0,colon);
        return value;
    }
    static int parsePort(String port){
        try{
            int value=Integer.parseInt(port==null?"":port.trim());
            if(value>=1&&value<=65535)return value;
        }catch(NumberFormatException ignored){}
        throw new IllegalArgumentException("端口应为 1–65535");
    }
}
