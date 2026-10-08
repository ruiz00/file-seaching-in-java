import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class UniversalFileSearch {

    // Options partagees entre toutes les taches (figees avant le lancement du pool)
    private static boolean useRegex = false;
    private static Pattern pattern;
    private static String keywordLower;
    private static Set<String> allowedExtensions = null; // null = toutes extensions acceptees
    private static int maxDepth = -1;                    // -1 = pas de limite
    private static BufferedWriter outputWriter = null;   // null = ecrire sur la console

    static class SearchTask extends RecursiveTask<Integer> {
        private final Path path;
        private final int depth;

        public SearchTask(Path path, int depth) {
            this.path = path;
            this.depth = depth;
        }

        @Override
        protected Integer compute() {
            if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                int totalMatches = 0;
                List<SearchTask> subTasks = new ArrayList<>();

                try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                    for (Path entry : stream) {
                        if (Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)) {
                            // on ne descend dans le sous-dossier que si la profondeur le permet
                            if (maxDepth == -1 || depth < maxDepth) {
                                subTasks.add(new SearchTask(entry, depth + 1));
                            }
                        } else {
                            subTasks.add(new SearchTask(entry, depth + 1));
                        }
                    }
                } catch (IOException e) {
                    System.err.println("Acces refuse au dossier: " + path);
                }

                invokeAll(subTasks);
                for (SearchTask task : subTasks) {
                    totalMatches += task.join();
                }
                return totalMatches;

            } else if (Files.isRegularFile(path)) {
                if (!extensionAllowed(path)) {
                    return 0;
                }
                return searchInFile(path);
            }
            return 0;
        }

        private boolean extensionAllowed(Path file) {
            if (allowedExtensions == null) {
                return true;
            }
            String name = file.getFileName().toString().toLowerCase();
            int dotIndex = name.lastIndexOf('.');
            if (dotIndex == -1) {
                return false; // pas d'extension alors qu'un filtre est actif
            }
            String ext = name.substring(dotIndex); // inclut le point, ex: ".java"
            return allowedExtensions.contains(ext);
        }

        private int searchInFile(Path file) {
            int matches = 0;

            try (BufferedReader reader = Files.newBufferedReader(file)) {
                String line;
                int lineNumber = 1;

                while ((line = reader.readLine()) != null) {
                    boolean isMatch = useRegex
                            ? pattern.matcher(line).find()
                            : line.toLowerCase().contains(keywordLower);

                    if (isMatch) {
                        report(String.format("[%s : Ligne %d] %s", file.getFileName(), lineNumber, line.trim()));
                        matches++;
                    }
                    lineNumber++;
                }
            } catch (MalformedInputException e) {
                // fichier binaire ou encodage non supporte, on l'ignore
            } catch (IOException e) {
                System.err.println("Erreur de lecture sur " + file.getFileName());
            }
            return matches;
        }
    }

    private static void report(String line) {
        if (outputWriter != null) {
            synchronized (outputWriter) {
                try {
                    outputWriter.write(line);
                    outputWriter.newLine();
                } catch (IOException e) {
                    System.err.println("Erreur d'ecriture dans le fichier de sortie: " + e.getMessage());
                }
            }
        } else {
            synchronized (System.out) {
                System.out.println(line);
            }
        }
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            printUsage();
            return;
        }

        Path dir = Paths.get(args[0]);
        String keyword = args[1];
        String outputPath = null;

        // ---- parsing des options ----
        for (int i = 2; i < args.length; i++) {
            switch (args[i]) {
                case "-r":
                    useRegex = true;
                    break;

                case "--ext":
                    if (i + 1 >= args.length) {
                        System.err.println("Erreur: --ext necessite une valeur, ex: --ext .java,.py");
                        return;
                    }
                    allowedExtensions = parseExtensions(args[++i]);
                    break;

                case "--max-depth":
                    if (i + 1 >= args.length) {
                        System.err.println("Erreur: --max-depth necessite une valeur entiere");
                        return;
                    }
                    try {
                        maxDepth = Integer.parseInt(args[++i]);
                        if (maxDepth < 0) {
                            System.err.println("Erreur: --max-depth doit etre >= 0");
                            return;
                        }
                    } catch (NumberFormatException e) {
                        System.err.println("Erreur: valeur invalide pour --max-depth");
                        return;
                    }
                    break;

                case "--output":
                    if (i + 1 >= args.length) {
                        System.err.println("Erreur: --output necessite un nom de fichier, ex: --output results.txt");
                        return;
                    }
                    outputPath = args[++i];
                    break;

                default:
                    System.err.println("Option inconnue: " + args[i]);
                    printUsage();
                    return;
            }
        }

        if (!Files.exists(dir)) {
            System.err.println("Erreur: le chemin n'existe pas: " + dir);
            return;
        }

        // ---- preparation de la recherche (regex ou texte simple) ----
        if (useRegex) {
            try {
                pattern = Pattern.compile(keyword, Pattern.CASE_INSENSITIVE);
            } catch (PatternSyntaxException e) {
                System.err.println("Erreur: expression reguliere invalide -> " + e.getMessage());
                return;
            }
        } else {
            keywordLower = keyword.toLowerCase();
        }

        // ---- preparation du fichier de sortie ----
        if (outputPath != null) {
            try {
                outputWriter = new BufferedWriter(new FileWriter(outputPath));
            } catch (IOException e) {
                System.err.println("Erreur: impossible de creer le fichier de sortie -> " + e.getMessage());
                return;
            }
        }

        System.out.println("Recherche de '" + keyword + "'" + (useRegex ? " (regex)" : "")
                + " dans " + dir
                + (allowedExtensions != null ? " [extensions: " + allowedExtensions + "]" : "")
                + (maxDepth != -1 ? " [profondeur max: " + maxDepth + "]" : "")
                + "...\n");

        ForkJoinPool pool = new ForkJoinPool();
        int total;
        try {
            SearchTask mainTask = new SearchTask(dir, 0);
            total = pool.invoke(mainTask);
        } finally {
            pool.shutdown();
        }

        if (outputWriter != null) {
            try {
                outputWriter.flush();
                outputWriter.close();
            } catch (IOException e) {
                System.err.println("Erreur lors de la fermeture du fichier de sortie: " + e.getMessage());
            }
            System.out.println("Resultats enregistres dans: " + outputPath);
        }

        System.out.println("\n Termine. " + total + " correspondance(s) trouvee(s).");
    }

    private static Set<String> parseExtensions(String raw) {
        Set<String> result = new HashSet<>();
        for (String ext : raw.split(",")) {
            ext = ext.trim().toLowerCase();
            if (ext.isEmpty()) continue;
            if (!ext.startsWith(".")) {
                ext = "." + ext;
            }
            result.add(ext);
        }
        return result;
    }

    private static void printUsage() {
        System.out.println("Usage: java UniversalFileSearch <chemin> <mot_cle> [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -r                     Traite <mot_cle> comme une expression reguliere");
        System.out.println("  --ext .java,.py        Ne cherche que dans les fichiers ayant ces extensions");
        System.out.println("  --max-depth N          Limite la recursion a N niveaux de sous-dossiers");
        System.out.println("  --output fichier.txt   Ecrit les resultats dans un fichier au lieu de la console");
    }
}