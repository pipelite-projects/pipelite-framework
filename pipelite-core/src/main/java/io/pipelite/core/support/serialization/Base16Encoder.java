package io.pipelite.core.support.serialization;

public class Base16Encoder implements BaseEncoder {

    @Override
    public String encode(byte[] byteArray) {
        StringBuilder hexStringBuffer = new StringBuilder();
        for (byte b : byteArray) {
            hexStringBuffer.append(byteToHex(b));
        }
        return hexStringBuffer.toString();
    }

    @Override
    public byte[] decode(String text) {
        if (text.length() % 2 == 1) {
            throw new IllegalArgumentException("Invalid hexadecimal text supplied");
        }

        byte[] bytes = new byte[text.length() / 2];
        for (int i = 0; i < text.length(); i += 2) {
            bytes[i / 2] = hexToByte(text.substring(i, i + 2));
        }
        return bytes;
    }

    private String byteToHex(byte num) {
        char[] hexDigits = new char[2];
        hexDigits[0] = Character.forDigit((num >> 4) & 0xF, 16);
        hexDigits[1] = Character.forDigit((num & 0xF), 16);
        return new String(hexDigits);
    }

    public byte hexToByte(String hexString) {
        int firstDigit = toDigit(hexString.charAt(0));
        int secondDigit = toDigit(hexString.charAt(1));
        return (byte) ((firstDigit << 4) + secondDigit);
    }

    private int toDigit(char hexChar) {
        int digit = Character.digit(hexChar, 16);
        if(digit == -1) {
            throw new IllegalArgumentException(String.format("Invalid hexadecimal character %s", hexChar));
        }
        return digit;
    }

}
