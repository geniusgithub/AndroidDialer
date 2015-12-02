package com.android.dialer.dialpad;

public class LatinSmartDialMap implements SmartDialMap {

    private static final char[] LATIN_LETTERS_TO_DIGITS = {
        '2', '2', '2', // A,B,C -> 2
        '3', '3', '3', // D,E,F -> 3
        '4', '4', '4', // G,H,I -> 4
        '5', '5', '5', // J,K,L -> 5
        '6', '6', '6', // M,N,O -> 6
        '7', '7', '7', '7', // P,Q,R,S -> 7
        '8', '8', '8', // T,U,V -> 8
        '9', '9', '9', '9' // W,X,Y,Z -> 9
    };

    @Override
    public boolean isValidDialpadAlphabeticChar(char ch) {
        return (ch >= 'a' && ch <= 'z');
    }

    @Override
    public boolean isValidDialpadNumericChar(char ch) {
        return (ch >= '0' && ch <= '9');
    }

    @Override
    public boolean isValidDialpadCharacter(char ch) {
        return (isValidDialpadAlphabeticChar(ch) || isValidDialpadNumericChar(ch));
    }

    /*
     * The switch statement in this function was generated using the python code:
     * from unidecode import unidecode
     * for i in range(192, 564):
     *     char = unichr(i)
     *     decoded = unidecode(char)
     *     # Unicode characters that decompose into multiple characters i.e.
     *     #  into ss are not supported for now
     *     if (len(decoded) == 1 and decoded.isalpha()):
     *         print "case '" + char + "': return '" + unidecode(char) +  "';"
     *
     * This gives us a way to map characters containing accents/diacritics to their
     * alphabetic equivalents. The unidecode library can be found at:
     * http://pypi.python.org/pypi/Unidecode/0.04.1
     *
     * Also remaps all upper case latin characters to their lower case equivalents.
     */
    @Override
    public char normalizeCharacter(char ch) {
        switch (ch) {
            case 'À': return 'a';
            case 'Á': return 'a';
            case 'Â': return 'a';
            case 'Ã': return 'a';
            case 'Ä': return 'a';
            case 'Å': return 'a';
            case 'Ç': return 'c';
            case 'È': return 'e';
            case 'É': return 'e';
            case 'Ê': return 'e';
            case 'Ë': return 'e';
            case 'Ì': return 'i';
            case 'Í': return 'i';
            case 'Î': return 'i';
            case 'Ï': return 'i';
            case 'Ð': return 'd';
            case 'Ñ': return 'n';
            case 'Ò': return 'o';
            case 'Ó': return 'o';
            case 'Ô': return 'o';
            case 'Õ': return 'o';
            case 'Ö': return 'o';
            case '×': return 'x';
            case 'Ø': return 'o';
            case 'Ù': return 'u';
            case 'Ú': return 'u';
            case 'Û': return 'u';
            case 'Ü': return 'u';
            case 'Ý': return 'u';
            case 'à': return 'a';
            case 'á': return 'a';
            case 'â': return 'a';
            case 'ã': return 'a';
            case 'ä': return 'a';
            case 'å': return 'a';
            case 'ç': return 'c';
            case 'è': return 'e';
            case 'é': return 'e';
            case 'ê': return 'e';
            case 'ë': return 'e';
            case 'ì': return 'i';
            case 'í': return 'i';
            case 'î': return 'i';
            case 'ï': return 'i';
            case 'ð': return 'd';
            case 'ñ': return 'n';
            case 'ò': return 'o';
            case 'ó': return 'o';
            case 'ô': return 'o';
            case 'õ': return 'o';
            case 'ö': return 'o';
            case 'ø': return 'o';
            case 'ù': return 'u';
            case 'ú': return 'u';
            case 'û': return 'u';
            case 'ü': return 'u';
            case 'ý': return 'y';
            case 'ÿ': return 'y';
            case 'Ā': return 'a';
            case 'ā': return 'a';
            case 'Ă': return 'a';
            case 'ă': return 'a';
            case 'Ą': return 'a';
            case 'ą': return 'a';
            case 'Ć': return 'c';
            case 'ć': return 'c';
            case 'Ĉ': return 'c';
            case 'ĉ': return 'c';
            case 'Ċ': return 'c';
            case 'ċ': return 'c';
            case 'Č': return 'c';
            case 'č': return 'c';
            case 'Ď': return 'd';
            case 'ď': return 'd';
            case 'Đ': return 'd';
            case 'đ': return 'd';
            case 'Ē': return 'e';
            case 'ē': return 'e';
            case 'Ĕ': return 'e';
            case 'ĕ': return 'e';
            case 'Ė': return 'e';
            case 'ė': return 'e';
            case 'Ę': return 'e';
            case 'ę': return 'e';
            case 'Ě': return 'e';
            case 'ě': return 'e';
            case 'Ĝ': return 'g';
            case 'ĝ': return 'g';
            case 'Ğ': return 'g';
            case 'ğ': return 'g';
            case 'Ġ': return 'g';
            case 'ġ': return 'g';
            case 'Ģ': return 'g';
            case 'ģ': return 'g';
            case 'Ĥ': return 'h';
            case 'ĥ': return 'h';
            case 'Ħ': return 'h';
            case 'ħ': return 'h';
            case 'Ĩ': return 'i';
            case 'ĩ': return 'i';
            case 'Ī': return 'i';
            case 'ī': return 'i';
            case 'Ĭ': return 'i';
            case 'ĭ': return 'i';
            case 'Į': return 'i';
            case 'į': return 'i';
            case 'İ': return 'i';
            case 'ı': return 'i';
            case 'Ĵ': return 'j';
            case 'ĵ': return 'j';
            case 'Ķ': return 'k';
            case 'ķ': return 'k';
            case 'ĸ': return 'k';
            case 'Ĺ': return 'l';
            case 'ĺ': return 'l';
            case 'Ļ': return 'l';
            case 'ļ': return 'l';
            case 'Ľ': return 'l';
            case 'ľ': return 'l';
            case 'Ŀ': return 'l';
            case 'ŀ': return 'l';
            case 'Ł': return 'l';
            case 'ł': return 'l';
            case 'Ń': return 'n';
            case 'ń': return 'n';
            case 'Ņ': return 'n';
            case 'ņ': return 'n';
            case 'Ň': return 'n';
            case 'ň': return 'n';
            case 'Ō': return 'o';
            case 'ō': return 'o';
            case 'Ŏ': return 'o';
            case 'ŏ': return 'o';
            case 'Ő': return 'o';
            case 'ő': return 'o';
            case 'Ŕ': return 'r';
            case 'ŕ': return 'r';
            case 'Ŗ': return 'r';
            case 'ŗ': return 'r';
            case 'Ř': return 'r';
            case 'ř': return 'r';
            case 'Ś': return 's';
            case 'ś': return 's';
            case 'Ŝ': return 's';
            case 'ŝ': return 's';
            case 'Ş': return 's';
            case 'ş': return 's';
            case 'Š': return 's';
            case 'š': return 's';
            case 'Ţ': return 't';
            case 'ţ': return 't';
            case 'Ť': return 't';
            case 'ť': return 't';
            case 'Ŧ': return 't';
            case 'ŧ': return 't';
            case 'Ũ': return 'u';
            case 'ũ': return 'u';
            case 'Ū': return 'u';
            case 'ū': return 'u';
            case 'Ŭ': return 'u';
            case 'ŭ': return 'u';
            case 'Ů': return 'u';
            case 'ů': return 'u';
            case 'Ű': return 'u';
            case 'ű': return 'u';
            case 'Ų': return 'u';
            case 'ų': return 'u';
            case 'Ŵ': return 'w';
            case 'ŵ': return 'w';
            case 'Ŷ': return 'y';
            case 'ŷ': return 'y';
            case 'Ÿ': return 'y';
            case 'Ź': return 'z';
            case 'ź': return 'z';
            case 'Ż': return 'z';
            case 'ż': return 'z';
            case 'Ž': return 'z';
            case 'ž': return 'z';
            case 'ſ': return 's';
            case 'ƀ': return 'b';
            case 'Ɓ': return 'b';
            case 'Ƃ': return 'b';
            case 'ƃ': return 'b';
            case 'Ɔ': return 'o';
            case 'Ƈ': return 'c';
            case 'ƈ': return 'c';
            case 'Ɖ': return 'd';
            case 'Ɗ': return 'd';
            case 'Ƌ': return 'd';
            case 'ƌ': return 'd';
            case 'ƍ': return 'd';
            case 'Ɛ': return 'e';
            case 'Ƒ': return 'f';
            case 'ƒ': return 'f';
            case 'Ɠ': return 'g';
            case 'Ɣ': return 'g';
            case 'Ɩ': return 'i';
            case 'Ɨ': return 'i';
            case 'Ƙ': return 'k';
            case 'ƙ': return 'k';
            case 'ƚ': return 'l';
            case 'ƛ': return 'l';
            case 'Ɯ': return 'w';
            case 'Ɲ': return 'n';
            case 'ƞ': return 'n';
            case 'Ɵ': return 'o';
            case 'Ơ': return 'o';
            case 'ơ': return 'o';
            case 'Ƥ': return 'p';
            case 'ƥ': return 'p';
            case 'ƫ': return 't';
            case 'Ƭ': return 't';
            case 'ƭ': return 't';
            case 'Ʈ': return 't';
            case 'Ư': return 'u';
            case 'ư': return 'u';
            case 'Ʊ': return 'y';
            case 'Ʋ': return 'v';
            case 'Ƴ': return 'y';
            case 'ƴ': return 'y';
            case 'Ƶ': return 'z';
            case 'ƶ': return 'z';
            case 'ƿ': return 'w';
            case 'Ǎ': return 'a';
            case 'ǎ': return 'a';
            case 'Ǐ': return 'i';
            case 'ǐ': return 'i';
            case 'Ǒ': return 'o';
            case 'ǒ': return 'o';
            case 'Ǔ': return 'u';
            case 'ǔ': return 'u';
            case 'Ǖ': return 'u';
            case 'ǖ': return 'u';
            case 'Ǘ': return 'u';
            case 'ǘ': return 'u';
            case 'Ǚ': return 'u';
            case 'ǚ': return 'u';
            case 'Ǜ': return 'u';
            case 'ǜ': return 'u';
            case 'Ǟ': return 'a';
            case 'ǟ': return 'a';
            case 'Ǡ': return 'a';
            case 'ǡ': return 'a';
            case 'Ǥ': return 'g';
            case 'ǥ': return 'g';
            case 'Ǧ': return 'g';
            case 'ǧ': return 'g';
            case 'Ǩ': return 'k';
            case 'ǩ': return 'k';
            case 'Ǫ': return 'o';
            case 'ǫ': return 'o';
            case 'Ǭ': return 'o';
            case 'ǭ': return 'o';
            case 'ǰ': return 'j';
            case 'ǲ': return 'd';
            case 'Ǵ': return 'g';
            case 'ǵ': return 'g';
            case 'Ƿ': return 'w';
            case 'Ǹ': return 'n';
            case 'ǹ': return 'n';
            case 'Ǻ': return 'a';
            case 'ǻ': return 'a';
            case 'Ǿ': return 'o';
            case 'ǿ': return 'o';
            case 'Ȁ': return 'a';
            case 'ȁ': return 'a';
            case 'Ȃ': return 'a';
            case 'ȃ': return 'a';
            case 'Ȅ': return 'e';
            case 'ȅ': return 'e';
            case 'Ȇ': return 'e';
            case 'ȇ': return 'e';
            case 'Ȉ': return 'i';
            case 'ȉ': return 'i';
            case 'Ȋ': return 'i';
            case 'ȋ': return 'i';
            case 'Ȍ': return 'o';
            case 'ȍ': return 'o';
            case 'Ȏ': return 'o';
            case 'ȏ': return 'o';
            case 'Ȑ': return 'r';
            case 'ȑ': return 'r';
            case 'Ȓ': return 'r';
            case 'ȓ': return 'r';
            case 'Ȕ': return 'u';
            case 'ȕ': return 'u';
            case 'Ȗ': return 'u';
            case 'ȗ': return 'u';
            case 'Ș': return 's';
            case 'ș': return 's';
            case 'Ț': return 't';
            case 'ț': return 't';
            case 'Ȝ': return 'y';
            case 'ȝ': return 'y';
            case 'Ȟ': return 'h';
            case 'ȟ': return 'h';
            case 'Ȥ': return 'z';
            case 'ȥ': return 'z';
            case 'Ȧ': return 'a';
            case 'ȧ': return 'a';
            case 'Ȩ': return 'e';
            case 'ȩ': return 'e';
            case 'Ȫ': return 'o';
            case 'ȫ': return 'o';
            case 'Ȭ': return 'o';
            case 'ȭ': return 'o';
            case 'Ȯ': return 'o';
            case 'ȯ': return 'o';
            case 'Ȱ': return 'o';
            case 'ȱ': return 'o';
            case 'Ȳ': return 'y';
            case 'ȳ': return 'y';
            case 'A': return 'a';
            case 'B': return 'b';
            case 'C': return 'c';
            case 'D': return 'd';
            case 'E': return 'e';
            case 'F': return 'f';
            case 'G': return 'g';
            case 'H': return 'h';
            case 'I': return 'i';
            case 'J': return 'j';
            case 'K': return 'k';
            case 'L': return 'l';
            case 'M': return 'm';
            case 'N': return 'n';
            case 'O': return 'o';
            case 'P': return 'p';
            case 'Q': return 'q';
            case 'R': return 'r';
            case 'S': return 's';
            case 'T': return 't';
            case 'U': return 'u';
            case 'V': return 'v';
            case 'W': return 'w';
            case 'X': return 'x';
            case 'Y': return 'y';
            case 'Z': return 'z';
            default:
                return ch;
        }
    }

    @Override
    public byte getDialpadIndex(char ch) {
        if (ch >= '0' && ch <= '9') {
            return (byte) (ch - '0');
        } else if (ch >= 'a' && ch <= 'z') {
            return (byte) (LATIN_LETTERS_TO_DIGITS[ch - 'a'] - '0');
        } else {
            return -1;
        }
    }

    @Override
    public char getDialpadNumericCharacter(char ch) {
        if (ch >= 'a' && ch <= 'z') {
            return LATIN_LETTERS_TO_DIGITS[ch - 'a'];
        }
        return ch;
    }

}
