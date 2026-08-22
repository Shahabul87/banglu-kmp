// ⚠ THROWAWAY SPIKE — delete with .github/workflows/windows-ime-msix-spike.yml.
//
// Run 1 of the spike killed the packaged app at start-up with
//   java.io.IOException: Unable to establish loopback connection
//   at java.net.http/jdk.internal.net.http.HttpClientImpl.<init>
//   at com.banglu.winime.update.Http.<init>(Updater.kt:244)
// while the SAME app image, unpackaged, ran for 180s without complaint.
//
// java.net.http.HttpClient's constructor opens a loopback socket pair for its
// selector wake-up, so this failure is about MSIX network isolation and not
// about our updater's URL. This file isolates that question from our app:
// launched inside the package container it answers, in order, whether a
// packaged process can (1) open a plain loopback socket pair at all and
// (2) construct an HttpClient. Whichever of the two fails is the real limit.
//
// Deliberately dependency-free and single-file so `java LoopbackProbe.java`
// runs it with no build step.
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.http.HttpClient;

public class LoopbackProbe {
    public static void main(String[] args) {
        System.out.println("LOOPBACK-PROBE start");
        try {
            System.out.println("LOOPBACK-PROBE identity.exe=" +
                ProcessHandle.current().info().command().orElse("(unknown)"));
        } catch (Throwable t) {
            System.out.println("LOOPBACK-PROBE identity.exe=(unavailable) " + t);
        }

        // 1. A raw loopback accept/connect — the primitive HttpClient needs.
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            try (Socket client = new Socket(InetAddress.getLoopbackAddress(), server.getLocalPort());
                 Socket accepted = server.accept()) {
                System.out.println("LOOPBACK-PROBE rawLoopback=OK port=" + server.getLocalPort()
                    + " connected=" + client.isConnected() + " accepted=" + accepted.isConnected());
            }
        } catch (Throwable t) {
            System.out.println("LOOPBACK-PROBE rawLoopback=FAILED " + t);
        }

        // 2. The exact call that killed the packaged app.
        try {
            HttpClient client = HttpClient.newHttpClient();
            System.out.println("LOOPBACK-PROBE httpClientCtor=OK " + client.version());
        } catch (Throwable t) {
            System.out.println("LOOPBACK-PROBE httpClientCtor=FAILED " + t);
        }

        System.out.println("LOOPBACK-PROBE end");
    }
}
