public class Main {
    public static void main(String[] args) {
        String inputPath = args.length > 0 ? args[0] : "Example.java";
        String outputPath = args.length > 1 ? args[1] : "Example.cpp";

        try {
            Translator translator = new Translator();
            translator.translateFile(inputPath, outputPath);
            System.out.println("Translation complete: " + outputPath);
        } catch (Exception e) {
            System.err.println("Translation failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}