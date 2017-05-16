package com.ks.lanya;

/**
 * Created by Admin on 2017/5/15 0015 11:38.
 * Author: kang
 * Email: kangsafe@163.com
 */

public class DeviceInfo {
    private String name;
    private String addr;

    public DeviceInfo() {

    }

    public DeviceInfo(String _name, String _info) {
        name = _name;
        addr = _info;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddr() {
        return addr;
    }

    public void setAddr(String addr) {
        this.addr = addr;
    }
}
