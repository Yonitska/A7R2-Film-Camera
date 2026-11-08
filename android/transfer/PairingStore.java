package org.hongen.sonylutprobe;

import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Properties;

/** The stable one-time pairing secret, stored atomically on the SD card.
 *
 * The token is never logged and never sent in a URL; the phone puts it in the
 * X-SonyLut-Token header. Wi-Fi SSID/address are remembered only so the camera
 * can show the last known values; the WPA2 passphrase stays in memory because
 * it can change with each Wi-Fi Direct group. */
final class PairingStore {
    private static final String VERSION="1";
    private static final String ALPHABET="ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int TOKEN_LENGTH=12;
    private static final String FILE="PAIR.JSN",TEMP="PAIR.TMP";
    private final File dir,file,temp;

    PairingStore(File dir)throws IOException {
        this.dir=dir.getCanonicalFile();
        LutLibrary.ensureDirectory(this.dir);
        file=new File(this.dir,FILE);temp=new File(this.dir,TEMP);
    }
    PairingStore(android.content.Context context)throws IOException {
        this(ProductFiles.transfer());
    }

    synchronized String token()throws IOException {
        Properties p=load(true);
        String token=p.getProperty("token","");
        if(!valid(token)){
            token=generate();
            p.setProperty("format",VERSION);
            p.setProperty("token",token);
            write(p);
        }
        return token;
    }
    synchronized String code()throws IOException {
        String token=token();
        StringBuilder out=new StringBuilder(token.length()+token.length()/4);
        for(int i=0;i<token.length();i++){
            if(i>0&&i%4==0)out.append('-');
            out.append(token.charAt(i));
        }
        return out.toString();
    }
    synchronized void rememberNetwork(String ssid,String address,int port)throws IOException {
        Properties p=load(true);
        p.setProperty("format",VERSION);
        if(ssid!=null)p.setProperty("ssid",ssid);
        if(address!=null)p.setProperty("address",address);
        p.setProperty("port",Integer.toString(port));
        write(p);
    }
    synchronized void rotate()throws IOException {
        Properties p=load(true);
        p.setProperty("format",VERSION);
        p.setProperty("token",generate());
        write(p);
    }

    private Properties load(boolean create)throws IOException {
        Properties p=new Properties();
        if(file.isFile()){
            try{
                p=LutLibrary.readProperties(file);
                if(valid(p.getProperty("token","")))return p;
            }catch(Exception ignored){/* regenerate below */}
        }
        if(!create)return p;
        p.setProperty("format",VERSION);
        p.setProperty("token",generate());
        write(p);
        return p;
    }
    private void write(Properties p)throws IOException {
        LutLibrary.ensureDirectory(dir);
        LutLibrary.writeProperties(temp,p);
        if(file.exists()&&!file.delete()){
            temp.delete();
            throw new IOException("无法更新配对信息");
        }
        if(!temp.renameTo(file)){
            temp.delete();
            throw new IOException("无法保存配对信息");
        }
    }
    private static String generate(){
        SecureRandom random=new SecureRandom();
        StringBuilder token=new StringBuilder(TOKEN_LENGTH);
        for(int i=0;i<TOKEN_LENGTH;i++)token.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        return token.toString();
    }
    private static boolean valid(String token){
        if(token==null||token.length()!=TOKEN_LENGTH)return false;
        for(int i=0;i<token.length();i++)if(ALPHABET.indexOf(token.charAt(i))<0)return false;
        return true;
    }
}
