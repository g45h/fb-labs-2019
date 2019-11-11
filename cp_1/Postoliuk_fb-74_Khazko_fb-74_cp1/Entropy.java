package crypto;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;

public class Entropy {

    static LinkedHashMap<String, Double> sortByValue(HashMap<String, Double> map) {
        return map.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Collections.reverseOrder()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
    }

    private static String processText(String alphabet, String text) {
        return text.toLowerCase()
                .replaceAll(String.format("[^%s]", alphabet), "")
                .trim()
                .replaceAll("\\s+", " ");
    }

    static HashMap<String, Double> monogramsFrequency(String alphabet, String rawText) {
        int i;
        String processedText = processText(alphabet, rawText);
        HashMap<String, Double> map = new HashMap<>();
        int alphabetLength = alphabet.length();
        int textLength = processedText.length();

        for (i = 0; i < alphabetLength; ++i) {
            map.put(alphabet.substring(i, i + 1), 0.0);
        }
        for (i = 0; i < textLength; ++i) {
            String currentLetter = processedText.substring(i, i + 1);
            map.computeIfPresent(currentLetter, (k, v) -> v + 1);
        }
        for (i = 0; i < alphabetLength; ++i) {
            map.put(alphabet.substring(i, i + 1), map.get(alphabet.substring(i, i + 1)) / (double)textLength);
        }
        return map;
    }

    private static HashMap<String, Double> bigramsFrequency(String alphabet, String rawText, boolean intersected) {
        int i;
        String processedText = processText(alphabet, rawText);
        HashMap<String, Double> map = new HashMap<>();
        int alphabetLength = alphabet.length();
        int textLength = processedText.length() - 1;

        for (i = 0; i < alphabetLength; ++i)
            for (int j = 0; j < alphabetLength; ++j)
                map.put(alphabet.substring(i, i + 1) + alphabet.substring(j, j + 1), 0.0);

        int step = intersected ? 1 : 2;
        int bigramsAmount = 0;
        for (i = 0; i < textLength; bigramsAmount++, i += step)
            map.put(processedText.substring(i, i + 2), map.get(processedText.substring(i, i + 2)) + 1.0);

        for (String bigram : map.keySet())
            map.put(bigram, map.get(bigram) / (double)bigramsAmount);

        return map;
    }

    private static void bigramsPrettyPrintForMSWord(String alphabet, HashMap<String, Double> bigrams, Path fileName) throws IOException {
        StringBuilder output;
        int length = alphabet.length();
        Files.deleteIfExists(fileName);
        for (int i = 0, j; i < length; i += 7) {
            output = new StringBuilder(" ");
            for (j = 0; j < 7 && i + j < length; j++)
                output.append(String.format(" %10s", alphabet.charAt(i + j)));

            Files.write(fileName, output.toString().getBytes(), CREATE, APPEND);

            output = new StringBuilder();
            for (Character letter1 : alphabet.toCharArray()) {
                output.append(String.format("%n%s", letter1));
                for (j = 0; j < 7 && i + j < length; j++) {
                    output.append(String.format(" %.8f", bigrams.get(letter1.toString() + alphabet.charAt(i + j))));
                }
            }
            output.append(String.format("%n"));
            Files.write(fileName, output.toString().getBytes(), APPEND);
        }
    }

    private static void monogramsPrettyPrint(HashMap<String, Double> monograms, Path fileName) throws IOException {
        String output = "letter  frequency";
        Files.write(fileName, Collections.singleton(output));
        for (Map.Entry<String, Double> pair : monograms.entrySet()) {
            output = String.format("%n%6s %.8f", pair.getKey(), pair.getValue());
            Files.write(fileName, output.getBytes(), APPEND);
        }
    }

    private static Double entropy(HashMap<String, Double> ngrams, int wordLength) {
        return ngrams.values().stream()
                .filter(probability -> probability != 0)
                .mapToDouble(probability -> -1 * probability * (Math.log(probability) / Math.log(2.0)))
                .sum() / wordLength;
    }

    public static void main(String[] args) throws IOException {
        String resourceDir = "resources\\";
        final String rawText = new String(Files.readAllBytes(Paths.get(resourceDir + "Batum.txt")));
        final String russianAlphabet = "абвгдеёжзийклмнопрстуфхцчшщъыьэюя";
        final String russianAlphabetWithSpace = russianAlphabet + " ";
        Double H0 = Math.log(russianAlphabet.length()) / Math.log(2.0);
        System.out.printf("%-37s = %f%n", "H0", H0);
        System.out.printf("%-37s = %f%n", "R0", (1.0 - H0 / H0));
        LinkedHashMap<String, Double> monogramsWithSpacesFrequency = sortByValue(monogramsFrequency(russianAlphabetWithSpace, rawText));
        monogramsPrettyPrint(monogramsWithSpacesFrequency, Paths.get(resourceDir + "monograms with spaces.txt"));
        Double H1 = entropy(monogramsWithSpacesFrequency, 1);
        System.out.printf("%-37s = %f%n", "H1 with spaces", H1);
        System.out.printf("%-37s = %f%n", "R1 with spaces", (1.0 - H1 / H0));
        LinkedHashMap<String, Double> monogramsWithoutSpacesFrequency = sortByValue(monogramsFrequency(russianAlphabet, rawText));
        monogramsPrettyPrint(monogramsWithoutSpacesFrequency, Paths.get(resourceDir + "monograms without spaces.txt"));
        H1 = entropy(monogramsWithoutSpacesFrequency, 1);
        System.out.printf("%-37s = %f%n", "H1 without spaces", H1);
        System.out.printf("%-37s = %f%n", "R1 without spaces", (1.0 - H1 / H0));
        HashMap<String, Double> intersectedBigramsWithSpacesFrequency = bigramsFrequency(russianAlphabetWithSpace, rawText, true);
        bigramsPrettyPrintForMSWord(russianAlphabetWithSpace, intersectedBigramsWithSpacesFrequency, Paths.get(resourceDir + "intersected bigrams with spaces.txt"));
        Double H2 = entropy(intersectedBigramsWithSpacesFrequency, 2);
        System.out.printf("%-37s = %f%n", "H2 for intersected with spaces", H2);
        System.out.printf("%-37s = %f%n", "R2 for intersected with spaces", (1.0 - H2 / H0));
        HashMap<String, Double> intersectedBigramsWithoutSpacesFrequency = bigramsFrequency(russianAlphabet, rawText, true);
        bigramsPrettyPrintForMSWord(russianAlphabet, intersectedBigramsWithoutSpacesFrequency, Paths.get(resourceDir + "intersected bigrams without spaces.txt"));
        H2 = entropy(intersectedBigramsWithoutSpacesFrequency, 2);
        System.out.printf("%-37s = %f%n", "H2 for intersected without spaces", H2);
        System.out.printf("%-37s = %f%n", "R2 for intersected without spaces", (1.0 - H2 / H0));
        HashMap<String, Double> notIntersectedBigramsWithSpacesFrequency = bigramsFrequency(russianAlphabetWithSpace, rawText, false);
        bigramsPrettyPrintForMSWord(russianAlphabetWithSpace, notIntersectedBigramsWithSpacesFrequency, Paths.get(resourceDir + "not intersected bigrams with spaces.txt"));
        H2 = entropy(notIntersectedBigramsWithSpacesFrequency, 2);
        System.out.printf("%-37s = %f%n", "H2 for not intersected with spaces", H2);
        System.out.printf("%-37s = %f%n", "R2 for not intersected with spaces", (1.0 - H2 / H0));
        HashMap<String, Double> notIntersectedBigramsWithoutSpacesFrequency = bigramsFrequency(russianAlphabet, rawText, false);
        bigramsPrettyPrintForMSWord(russianAlphabet, notIntersectedBigramsWithoutSpacesFrequency, Paths.get(resourceDir + "not intersected bigrams without spaces.txt"));
        H2 = entropy(notIntersectedBigramsWithoutSpacesFrequency, 2);
        System.out.printf("%-37s = %f%n", "H2 for not intersected without spaces", H2);
        System.out.printf("%-37s = %f%n", "R2 for not intersected without spaces", (1.0 - H2 / H0));
    }
}
