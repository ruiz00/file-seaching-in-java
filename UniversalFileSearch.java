import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

public class UniversalFileSearch{
    static class SearchTask extends RecursiveTask<Integer>{
        private final Path path;
        private final String keyword;

        public SearchTask(Path path,String keyword){
            this.path = path;
            this.keyword = keyword;
        }

        @Override
        protected Integer compute(){
            if(Files.isDirectory(path)){
                int totalMatches = 0;
                List<SearchTask> subTasks = new ArrayList<>();

                try(DirectoryStream<Path> stream = Files.newDirectoryStream(path)){
                    for (Path entry : stream){
                        SearchTask task = new SearchTask(entry, keyword);
                        subTasks.add(task);
                    }
                }catch (IOException e){
                    System.err.println("Acces refuse au dossier: "+ path);
                }
                invokeAll(subTasks);//  execute les sous-taches en parallele

                for (SearchTask task: subTasks){
                    totalMatches += task.join();//addition des resultats
                }
                return totalMatches;
            }else if(Files.isRegularFile(path)){
                return searchInFile(path,keyword);
            }
            return 0;
        }
        private int searchInFile(Path file,String keyword){
            int matches = 0;

            try(BufferedReader reader = Files.newBufferedReader(file)){
                String line;
                int lineNumber = 1;

                while((line= reader.readLine()) != null){
                    if(line.toLowerCase().contains(keyword.toLowerCase())){
                        synchronized(System.out){
                            System.out.printf("[%s : Ligne %d] %s%n", file.getFileName(),lineNumber,line.trim());
                        }
                        matches++;
                    }
                    lineNumber++;
                }
            }catch (MalformedInputException e){
                // c'est un fichier texte lisible on passe au suivant 
            }catch(IOException e){
                System.err.println("Erreur de lecture sur " + file.getFileName());
            }
            return matches;
        }
    }
    public static void main(String[] args){
        if(args.length < 2){
            System.out.println("Usage: java UniversalFileSearch <chemin_dossier> <mot_cle>");
            return;
        }
        Path dir = Paths.get(args[0]);
        String keyword = args[1];

        if(!Files.exists(dir)){
            System.err.println("Erreur: le dossier n'existe pas");
            return;
        }
        try(ForkJoinPool pool = new ForkJoinPool()){
            System.out.println("Recherche de '" + keyword + "' dans TOUS les fichier de " + dir + "...\n");

            SearchTask mainTask = new SearchTask(dir , keyword);
            int total = pool.invoke(mainTask);

            System.out.println("\n Termine. " + total + " correspondance(s) trouvee(s).");
        }

    }
}