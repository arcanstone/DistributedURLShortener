import java.io.*;
import java.net.*;
import java.util.concurrent.*;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.PrintWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Date;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class URLShortner {

    static final File WEB_ROOT = new File(".");
    static final String DEFAULT_FILE = "index.html";
    static final String FILE_NOT_FOUND = "404.html";
    static final String METHOD_NOT_SUPPORTED = "not_supported.html";
    static final String REDIRECT_RECORDED = "redirect_recorded.html";
    static final String REDIRECT = "redirect.html";
    static final String NOT_FOUND = "notfound.html";
    static URLShortnerDB database = null;
    static final int PORT = 8086;

    // Thread pool configuration: let's allow 10 concurrent threads
    private static final ExecutorService threadPool = Executors.newFixedThreadPool(8);

    public static void main(String[] args) {
        database = new URLShortnerDB();

        try (ServerSocket serverConnect = new ServerSocket(PORT)) {

            //Waits for all threads to complete before shutdown
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Shutdown hook triggered. Closing server...");
                try {
                    serverConnect.close();  // Close the ServerSocket to stop accepting new connections
                } catch (IOException e) {
                    System.err.println("Error while closing server socket: " + e.getMessage());
                }
                threadPool.shutdown();  // Shutdown the thread pool
                try {
                    if (!threadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                        System.err.println("Forcing shutdown of thread pool...");
                        threadPool.shutdownNow();  // Forcefully shutdown the pool if tasks don't terminate
                    }
                } catch (InterruptedException e) {
                    threadPool.shutdownNow();
                }
                System.out.println("Server shut down gracefully.");
            }));

            System.out.println("Server started.\nListening for connections on port : " + PORT + " ...\n");

            // Accept connections and handle each one in a separate thread from the thread pool
            while (true) {
                Socket clientSocket = serverConnect.accept();
                threadPool.submit(() -> handle(clientSocket));  // Submit the task to the thread pool
            }

        } 
        catch (SocketException e) {
                // This is where the exception is caught when the ServerSocket is closed
                System.out.println("Server socket closed, stopping server...");
        }
        catch (IOException e) {
            System.err.println("Server Connection error : " + e.getMessage());
        } finally {
            threadPool.shutdown();  // Shutdown the thread pool when done
        }
    }

    public static void handle(Socket connect) {
        BufferedReader in = null;
        PrintWriter out = null;
        BufferedOutputStream dataOut = null;

        try {
            in = new BufferedReader(new InputStreamReader(connect.getInputStream()));
            out = new PrintWriter(connect.getOutputStream());
            dataOut = new BufferedOutputStream(connect.getOutputStream());

            String input = in.readLine();

            // Handle PUT and GET requests
            Pattern pput = Pattern.compile("^PUT\\s+/\\?short=(\\S+)&long=(\\S+)\\s+(\\S+)$");
            Matcher mput = pput.matcher(input);
            if (mput.matches()) {
                String shortResource = mput.group(1);
                String longResource = mput.group(2);
                String httpVersion = mput.group(3);

                // Save to the database (possibly concurrent access)
                database.save(shortResource, longResource);

                // Return response to client
                sendResponse(out, dataOut, REDIRECT_RECORDED, "text/html", 200);

            }
            else if (input != null && input.startsWith("GET /sync-data")) {
                // Handle the sync-data request to copy data from another server
                String sourceServer = extractServerFromSyncRequest(input);
                syncDataFromServer(sourceServer, out, dataOut);

            }
            else {
                Pattern pget = Pattern.compile("^(\\S+)\\s+/(\\S+)\\s+(\\S+)$");
                Matcher mget = pget.matcher(input);
                if (mget.matches()) {
                    String shortResource = mget.group(2);

                    // Find in the database (possibly concurrent access)
                    String longResource = database.find(shortResource);
                    if (longResource != null) {
                        sendRedirect(out, longResource, dataOut);
                    } else {
                        sendResponse(out, dataOut, FILE_NOT_FOUND, "text/html", 404);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Server error: " + e.getMessage());
        } finally {
            try {
                in.close();
                out.close();
                connect.close();
            } catch (Exception e) {
                System.err.println("Error closing stream: " + e.getMessage());
            }
        }
    }

    /**
     * Extracts the server address from the /sync-data request line.
     */
    private static String extractServerFromSyncRequest(String requestLine) {
        Pattern pattern = Pattern.compile("/sync-data\\?source=([^\\s]+)");
        Matcher matcher = pattern.matcher(requestLine);
        if (matcher.find()) {
            return matcher.group(1);  // Return the source server address from the query parameter
        }
        return null;
    }

    private static void sendResponse(PrintWriter out, BufferedOutputStream dataOut, String fileName, String contentType, int statusCode) throws IOException {
        File file = new File(WEB_ROOT, fileName);
        int fileLength = (int) file.length();
        byte[] fileData = readFileData(file, fileLength);

        out.println("HTTP/1.1 " + statusCode + " OK");
        out.println("Server: Java HTTP Server/Shortner : 1.0");
        out.println("Date: " + new Date());
        out.println("Content-type: " + contentType);
        out.println("Content-length: " + fileLength);
        out.println();
        out.flush();

        dataOut.write(fileData, 0, fileLength);
        dataOut.flush();
    }

    private static void sendRedirect(PrintWriter out, String location, BufferedOutputStream dataOut) throws IOException {
        out.println("HTTP/1.1 307 Temporary Redirect");
        out.println("Location: " + location);
        out.println("Server: Java HTTP Server/Shortner : 1.0");
        out.println("Date: " + new Date());
        out.println();
        out.flush();
    }

    private static byte[] readFileData(File file, int fileLength) throws IOException {
        FileInputStream fileIn = null;
        byte[] fileData = new byte[fileLength];

        try {
            fileIn = new FileInputStream(file);
            fileIn.read(fileData);
        } finally {
            if (fileIn != null)
                fileIn.close();
        }
        return fileData;
    }
}
