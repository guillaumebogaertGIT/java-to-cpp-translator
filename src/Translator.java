import java.util.Scanner;
import java.io.File;
import java.io.FileNotFoundException;

public class Translator {
    File file = new File ("Example.java");

public void readFile() throws FileNotFoundException {
    Scanner scanner = new Scanner (file);
    while (scanner.hasNextLine()) {
        String line = scanner.nextLine();
        System.out.println(line);
        
    }

}
}
