package claimshield.ui;

/**
 * @author Nguyen Ba Nam S3974998
 */

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.NoSuchElementException;
import java.util.Scanner;

/**
 * Collects and sanity checks every value typed by the administrator.
 *
 * Holding all console reading in one class means the menu code never touches a
 * Scanner, never repeats a re-prompt loop, and can offer the same escape hatch
 * everywhere: typing cancel at any prompt aborts the current operation and
 * returns to the previous screen. A closed input stream is treated as a cancel
 * as well, so the program never crashes when it is fed a script instead of a
 * live keyboard.
 */
public class InputReader {

    /** Returned by {@link #readMenuChoice} when no more input is available. */
    public static final int END_OF_INPUT = -1;

    /** Word an administrator can type at any prompt to abort an operation. */
    public static final String CANCEL_WORD = "cancel";

    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    };
    private static final DateTimeFormatter DATE_ONLY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final Scanner scanner;

    /**
     * @param scanner source of console input, normally wrapping System.in
     */
    public InputReader(Scanner scanner) {
        this.scanner = scanner;
    }

    /**
     * Asks for a menu number and keeps asking until it is inside the range.
     *
     * @param prompt     question shown to the administrator
     * @param minChoice  smallest accepted number
     * @param maxChoice  largest accepted number
     * @return the chosen number, or {@link #END_OF_INPUT} when input has ended
     */
    public int readMenuChoice(String prompt, int minChoice, int maxChoice) {
        while (true) {
            System.out.print(prompt);
            String line = readLine();
            if (line == null) {
                return END_OF_INPUT;
            }
            try {
                int choice = Integer.parseInt(line.trim());
                if (choice >= minChoice && choice <= maxChoice) {
                    return choice;
                }
                System.out.println("  Please enter a number between " + minChoice + " and " + maxChoice + ".");
            } catch (NumberFormatException exception) {
                System.out.println("  \"" + line.trim() + "\" is not a number. Please try again.");
            }
        }
    }

    /**
     * Asks for a non empty piece of text.
     *
     * @param prompt question shown to the administrator
     * @return the trimmed text, or null when the operation was cancelled
     */
    public String readText(String prompt) {
        while (true) {
            System.out.print(prompt);
            String line = readLine();
            if (line == null) {
                return null;
            }
            String trimmed = line.trim();
            if (trimmed.equalsIgnoreCase(CANCEL_WORD)) {
                return null;
            }
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
            System.out.println("  This value cannot be empty. Type " + CANCEL_WORD + " to go back.");
        }
    }

    /**
     * Asks for text that must match a regular expression, for example an id.
     *
     * @param prompt      question shown to the administrator
     * @param regex       pattern the value must match
     * @param formatHint  description of the pattern shown after a bad attempt
     * @return the accepted text, or null when the operation was cancelled
     */
    public String readPattern(String prompt, String regex, String formatHint) {
        while (true) {
            String value = readText(prompt);
            if (value == null) {
                return null;
            }
            if (value.matches(regex)) {
                return value;
            }
            System.out.println("  Expected format: " + formatHint);
        }
    }

    /**
     * Asks for a monetary amount greater than zero.
     *
     * @param prompt question shown to the administrator
     * @return the amount, or null when the operation was cancelled
     */
    public Double readPositiveAmount(String prompt) {
        while (true) {
            String value = readText(prompt);
            if (value == null) {
                return null;
            }
            try {
                double amount = Double.parseDouble(value);
                if (amount > 0) {
                    return amount;
                }
                System.out.println("  The amount must be greater than zero.");
            } catch (NumberFormatException exception) {
                System.out.println("  \"" + value + "\" is not a number, for example 1250.50");
            }
        }
    }

    /**
     * Asks for a moment in time. A date on its own is read as midnight, and an
     * empty answer can be allowed to mean the current moment.
     *
     * @param prompt       question shown to the administrator
     * @param emptyMeansNow true when an empty answer should return the current time
     * @return the moment, or null when the operation was cancelled
     */
    public LocalDateTime readDateTime(String prompt, boolean emptyMeansNow) {
        while (true) {
            System.out.print(prompt);
            String line = readLine();
            if (line == null) {
                return null;
            }
            String trimmed = line.trim();
            if (trimmed.equalsIgnoreCase(CANCEL_WORD)) {
                return null;
            }
            if (trimmed.isEmpty()) {
                if (emptyMeansNow) {
                    return LocalDateTime.now().withNano(0);
                }
                System.out.println("  A date is required, for example 2026-07-10T14:30:00");
                continue;
            }
            LocalDateTime parsed = tryParseDateTime(trimmed);
            if (parsed != null) {
                return parsed;
            }
            System.out.println("  Use 2026-07-10T14:30:00, 2026-07-10 14:30 or 2026-07-10.");
        }
    }

    /**
     * Asks a yes or no question.
     *
     * @param prompt question shown to the administrator
     * @return true only when the answer starts with y
     */
    public boolean readYesNo(String prompt) {
        System.out.print(prompt);
        String line = readLine();
        if (line == null) {
            return false;
        }
        String trimmed = line.trim().toLowerCase();
        return trimmed.startsWith("y");
    }

    /** Waits for the administrator to acknowledge a screen. */
    public void waitForEnter() {
        System.out.print("  Press Enter to continue...");
        readLine();
    }

    /**
     * @param value text typed by the administrator
     * @return the parsed moment, or null when no supported format matched
     */
    private LocalDateTime tryParseDateTime(String value) {
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return LocalDateTime.parse(value, format);
            } catch (DateTimeParseException ignored) {
                // Try the next supported layout.
            }
        }
        try {
            return java.time.LocalDate.parse(value, DATE_ONLY_FORMAT).atStartOfDay();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    /**
     * @return the next line of input, or null when the stream has ended
     */
    private String readLine() {
        try {
            if (!scanner.hasNextLine()) {
                return null;
            }
            return scanner.nextLine();
        } catch (NoSuchElementException | IllegalStateException exception) {
            return null;
        }
    }
}
