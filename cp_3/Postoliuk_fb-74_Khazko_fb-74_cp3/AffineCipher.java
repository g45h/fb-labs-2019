package crypto;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static crypto.Entropy.sortByValue;


public class AffineCipher {
    private static final String RUSSIAN_ALPHABET = "абвгдежзийклмнопрстуфхцчшщьыэюя"; // There are no "ё", "ь".
    private static final int RUSSIAN_ALPHABET_LENGTH = 31;

    private static List<String> RUSSIAN_FREQUENTEST_BIGRAMS = List.of("ст", "но", "то", "на", "ен");

    private static int modInverse(int a, int m) {
        if (m <= 0) {
            throw new IllegalArgumentException("Modulus must be a positive integer!");
        }
        a %= m;
        int b = m, u0 = 1, u1 = 0;

        while (b != 0) {
            int tmp1 = b;
            int tmp2 = u0 - u1 * (a / b);

            b = a % b;
            a = tmp1;

            u0 = u1;
            u1 = tmp2;
        }

        if (a > 1) {
            throw new ArithmeticException(String.format("%d has no multiplicative inverse by modulo %d!", a, m));
        }
        // make positive if negative
        return (u0 + m) % m;
    }

    private static List<Integer> solveLinearCongruence(int a, int b, int m) {
        // get positive modulo
        a = (a % m + m) % m;
        // get positive modulo
        b = (b % m + m) % m;
        int a0 = a;
        int b0 = m;

        while (b0 != 0) {
            int temp = b0;
            b0 = a0 % b0;
            a0 = temp;
        }

        // get positive modulo
        int d = (a0 + m) % m;

        if (b % d != 0) {
            throw new IllegalArgumentException(
                    String.format(
                            "Congruence %sx = %s mod (%s) has no solutions, because %s is not divisible by %s! ",
                            a, b, m, b, d
                    )
            );
        }

        int a1 = a / d;
        int m1 = m / d;
        // final is needed for lambdas below
        final int b1 = b / d * modInverse(a1, m1) % m1;

        return IntStream.range(0, d).boxed()
                .map(x -> b1 + (x * m1))
                .collect(Collectors.toList());
    }

    private static boolean isTextInformative(String text) {
        return VigenereCipher.coincidenceIndex(text) > 0.05;
    }

    private static String decrypt(String cipherText, Integer a, Integer b) {
        StringBuilder plainText = new StringBuilder();
        int modulus = RUSSIAN_ALPHABET_LENGTH, modulusSquare = modulus * modulus;
        int i = 0, X, Y;
        for (int length = cipherText.length(); i < length; ) {
            Y = RUSSIAN_ALPHABET.indexOf(cipherText.charAt(i++)) * modulus + RUSSIAN_ALPHABET.indexOf(cipherText.charAt(i++));
            try {
                X = modInverse(a, modulusSquare) * (Y - b + modulusSquare) % modulusSquare;
            } catch (Exception e) {
                return "";
            }
            plainText.append(RUSSIAN_ALPHABET.charAt(X / modulus));
            plainText.append(RUSSIAN_ALPHABET.charAt(X % modulus));
        }

        return plainText.toString();
    }

    private static String decrypt(String cipherText) {
        HashMap<String, Double> bigramsFrequency = Entropy.bigramsFrequency(RUSSIAN_ALPHABET, cipherText, false);
        ArrayList<String> textFrequentestBigrams = new ArrayList<>(sortByValue(bigramsFrequency).keySet());

        List<String> x1List = new ArrayList<>(), x2List = new ArrayList<>(), y1List = new ArrayList<>(), y2List = new ArrayList<>();
        for (int i = 0; i < 5; i++)
            for (int k = i + 1; k < 5; k++)
                for (int j = 0; j <= k; j++)
                    for (int l = 0; l <= k; l++)
                        if (l != j) {
                            x1List.add(RUSSIAN_FREQUENTEST_BIGRAMS.get(i));
                            x2List.add(RUSSIAN_FREQUENTEST_BIGRAMS.get(k));
                            y1List.add(textFrequentestBigrams.get(j));
                            y2List.add(textFrequentestBigrams.get(l));
                        }

        int X1, X2, Y1, Y2, modulus = RUSSIAN_ALPHABET_LENGTH, modulusSquare = modulus * modulus;
        List<Integer> aSolutions, bSolutions;

        for (int i = 0; i < x1List.size() ; i++) {
            X1 = RUSSIAN_ALPHABET.indexOf(x1List.get(i).charAt(0)) * modulus + RUSSIAN_ALPHABET.indexOf(x1List.get(i).charAt(1));
            Y1 = RUSSIAN_ALPHABET.indexOf(y1List.get(i).charAt(0)) * modulus + RUSSIAN_ALPHABET.indexOf(y1List.get(i).charAt(1));
            X2 = RUSSIAN_ALPHABET.indexOf(x2List.get(i).charAt(0)) * modulus + RUSSIAN_ALPHABET.indexOf(x2List.get(i).charAt(1));
            Y2 = RUSSIAN_ALPHABET.indexOf(y2List.get(i).charAt(0)) * modulus + RUSSIAN_ALPHABET.indexOf(y2List.get(i).charAt(1));
            System.out.println("X1=" + x1List.get(i) + " Y1=" + y1List.get(i));
            System.out.println("X2=" + x2List.get(i) + " Y2=" + y2List.get(i));
            aSolutions = solveLinearCongruence(X1 - X2, Y1 - Y2, modulusSquare);
            System.out.println("Potential a values: " + aSolutions);
            for (Integer a : aSolutions) {
                bSolutions = solveLinearCongruence(1, Y1 - a * X1, modulusSquare);
                for (Integer b : bSolutions) {
                    System.out.println("Potential b values for a=" + a + ": " + bSolutions);
                    System.out.print("For a=" + a + " b=" + b);
                    String plainText = decrypt(cipherText, a, b);
                    if (isTextInformative(plainText)) {
                        System.out.println(" text is informative");
                        return plainText;
                    } else {
                        System.out.println(" text is not informative");
                    }
                    System.out.println();
                }
            }
        }
        return "";
    }

    public static void main(String[] args) throws IOException {
        String encryptedText = Files.readString(Paths.get("resources", "lab3-11.txt"));
        encryptedText = encryptedText.toLowerCase()
                // remove all non-russian characters
                .replaceAll(String.format("[^%s]", RUSSIAN_ALPHABET), "")
                .replaceAll("ё", "е")
                .replaceAll("ъ", "ь")
                // trim trailing and leading spaces
                .trim()
                // replace multiple spaces with single
                .replaceAll("\\s+", " ");
        String decryptedText = decrypt(encryptedText);
        System.out.println("Encrypted text: " + encryptedText);
        System.out.println("Decrypted text: " + decryptedText);
    }

}
