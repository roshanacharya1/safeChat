package com.example.android.safechat;
//message structure
public class messageStruct {
    private String text;
    private String name;

    public messageStruct() {
    }

    public messageStruct(String text, String name) {
        this.text = text;
        this.name = name;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}
