package com.android.dialer.dialpad;

/**
 * Note: These methods currently take characters as arguments. For future planned language support,
 * they will need to be changed to use codepoints instead of characters.
 *
 * http://docs.oracle.com/javase/6/docs/api/java/lang/String.html#codePointAt(int)
 *
 * If/when this change is made, LatinSmartDialMap(which operates on chars) will continue to work
 * by simply casting from a codepoint to a character.
 */
public interface SmartDialMap {
    /*
     * Returns true if the provided character can be mapped to a key on the dialpad
     */
    public boolean isValidDialpadCharacter(char ch);

    /*
     * Returns true if the provided character is a letter, and can be mapped to a key on the dialpad
     */
    public boolean isValidDialpadAlphabeticChar(char ch);

    /*
     * Returns true if the provided character is a digit, and can be mapped to a key on the dialpad
     */
    public boolean isValidDialpadNumericChar(char ch);

    /*
     * Get the index of the key on the dialpad which the character corresponds to
     */
    public byte getDialpadIndex(char ch);

    /*
     * Get the actual numeric character on the dialpad which the character corresponds to
     */
    public char getDialpadNumericCharacter(char ch);

    /*
     * Converts uppercase characters to lower case ones, and on a best effort basis, strips accents
     * from accented characters.
     */
    public char normalizeCharacter(char ch);
}
