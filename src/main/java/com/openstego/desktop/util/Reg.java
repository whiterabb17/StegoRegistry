package com.openstego.desktop.util;

import java.io.IOException;


public class Reg {
    /*
    public void checkInstalled(){
        try {
            String regValue = null;
            regValue = WinRegistry.valueForKey(WinRegistry.HKEY_LOCAL_MACHINE, setWin, "InstallDir");
            if(regValue == null){
                System.err.println("Application not installed!");
            } else {
                "do the other thing"
            }
        } catch (IllegalArgumentException | IllegalAccessException | InvocationTargetException | IOException ex) {
            System.err.println(ex);
        }
    }
    */
    public static String _contentValue = "";
    public static String _keyValue = "";

    public static void CheckRegistry(String name) {
        _contentValue = WinRegistry.readRegistry("HKCU\\Software\\" + name, "Content");
        _keyValue = WinRegistry.readRegistry("HKCU\\SOFTWARE\\" + name, "Key");
        System.out.println("Content Value: " + _contentValue);
        System.out.println("Key Value: " + _keyValue);//ValueName
    }
    public static void SetReg(String name, String keyValue) throws IOException {
        Runtime.getRuntime().exec("reg add HKCU\\SOFTWARE\\" + name + " /v Content /t REG_SZ /d " + keyValue);
    }
    public static void DeleteReg(String name) throws IOException {
        Runtime.getRuntime().exec("reg delete HKCU\\SOFTWARE\\" + name + " /va /f");
    }
}
