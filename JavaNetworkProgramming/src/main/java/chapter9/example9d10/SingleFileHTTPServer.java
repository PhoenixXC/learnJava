package chapter9.example9d10;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLConnection;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author      xuanc
 * @date        2019/12/10 下午3:46
 * @version     1.0
 */ 
public class SingleFileHTTPServer {

    private static final Logger log = LoggerFactory.getLogger("SingleFileHTTPServer");

    private final byte[] content;
    private final byte[] header;
    private final int port;
    private final String encoding;

    public SingleFileHTTPServer(String data, String encoding, String mimeType, int port)
            throws UnsupportedEncodingException {
        this(data.getBytes(encoding), encoding, mimeType, port);
    }

    public SingleFileHTTPServer(byte[] data, String encoding, String mimeType, int port) {
        this.content = data;
        this.port = port;
        this.encoding = encoding;
        String header = "HTTP/1.0 200 OK\r\n" +
                "Server: OneFile 2.0\r\n" +
                "Content-length: " + this.content.length + "\r\n" +
                "Content-type: " + mimeType + "; charset=" + encoding + "\r\n\r\n";
        this.header = header.getBytes(StandardCharsets.US_ASCII);
    }

    public void start() {
        ExecutorService pool = Executors.newFixedThreadPool(100);
        try (ServerSocket server = new ServerSocket(this.port)) {
            log.info("Accepting connections on port " + server.getLocalPort());
            log.info("Data to be sent");
            log.info(new String(this.content, encoding));

            while (true) {
                try {
                    Socket connection = server.accept();
                    pool.submit(new HTTPHandler(connection));
                } catch (IOException ex) {
                    log.warn("Exception accepting connection", ex);
                } catch (RuntimeException ex) {
                    log.error("Unexpected error", ex);
                }
            }
        } catch (IOException e) {
            log.error("Could not start server", e);
        }
    }

    private class HTTPHandler implements Callable<Void> {

        private final Socket connection;

        public HTTPHandler(Socket connection) {
            this.connection = connection;
        }

        @Override
        public Void call() throws Exception {
            try {
                OutputStream out = new BufferedOutputStream(connection.getOutputStream());
                InputStream in = new BufferedInputStream(connection.getInputStream());

                // 只读取第一行
                StringBuilder request = new StringBuilder(80);
                while (true) {
                    int c = in.read();
                    if (c == '\r' || c == '\n' || c == -1) {
                        break;
                    }
                    request.append(header);
                }
                out.write(content);
                out.flush();
            } catch (IOException ex) {
                log.warn("Error writing to client", ex);
            } finally {
                connection.close();
            }
            return null;
        }
    }

    public static void main(String[] args) {
        int port;
        try {
            port = Integer.parseInt(args[1]);
            if (port < 1 || port > 65535) {
                port = 1880;
            }
        } catch (RuntimeException ex) {
            port = 1880;
        }

        String encoding = "UTF-8";
        if (args.length > 2) {
            encoding = args[2];
        }

        try {
            Path path = Paths.get(args[0]);
            byte[] data = Files.readAllBytes(path);

            String contentType = URLConnection.getFileNameMap().getContentTypeFor(args[0]);
            SingleFileHTTPServer server = new SingleFileHTTPServer(data, encoding, contentType, port);
            server.start();
        } catch (ArrayIndexOutOfBoundsException ex) {
            log.warn("ArrayIndexOutOfBoundsException", ex);
        } catch (IOException ex) {
            log.warn("IO error", ex);
        }

    }

}
