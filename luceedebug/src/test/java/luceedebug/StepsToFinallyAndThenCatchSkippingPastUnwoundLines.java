package luceedebug;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

import com.github.dockerjava.api.DockerClient;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.javanet.NetHttpTransport;

import luceedebug.testutils.DapUtils;
import luceedebug.testutils.DockerUtils;
import luceedebug.testutils.LuceeUtils;
import luceedebug.testutils.TestParams.DockerInfo;
import luceedebug.testutils.DockerUtils.HostPortBindings;

import org.eclipse.lsp4j.debug.launch.DSPLauncher;

class StepsToFinallyAndThenCatchSkippingPastUnwoundLines {
    @ParameterizedTest
    @MethodSource("luceedebug.testutils.TestParams#getDockerFilePaths")
    void a(DockerInfo dockerInfo) throws Throwable {
        final DockerClient dockerClient = DockerUtils.getDefaultDockerClient();

        final String imageID = DockerUtils
            .buildOrGetImage(dockerClient, dockerInfo.dockerFile)
            .getImageID();

        final String containerID = DockerUtils
            .getFreshDefaultContainer(
                dockerClient,
                imageID,
                dockerInfo.luceedebugProjectRoot.toFile(),
                dockerInfo.getTestWebRoot("step_to_catch_block"),
                new int[][]{
                    new int[]{8888,8888},
                    new int[]{10000,10000}
                }
            )
            .getContainerID();

        dockerClient
            .startContainerCmd(containerID)
            .exec();

        HostPortBindings portBindings = DockerUtils.getPublishedHostPortBindings(dockerClient, containerID);
      
        try {
            LuceeUtils.pollForServerIsActive("http://localhost:" + portBindings.http + "/heartbeat.cfm");

            final var dapClient = new DapUtils.MockClient();

            final var FIXME_socket_needs_close = new Socket();
            FIXME_socket_needs_close.connect(new InetSocketAddress("localhost", portBindings.dap));
            final var launcher = DSPLauncher.createClientLauncher(dapClient, FIXME_socket_needs_close.getInputStream(), FIXME_socket_needs_close.getOutputStream());
            launcher.startListening();
            final var dapServer = launcher.getRemoteProxy();

            DapUtils.init(dapServer).join();
            DapUtils.attach(dapServer).join();

            DapUtils
                .setBreakpoints(dapServer, "/var/www/a.cfm", 29)
                .join();

            final var requestThreadToBeBlockedByBreakpoint = new java.lang.Thread(() -> {
                final var requestFactory = new NetHttpTransport().createRequestFactory();
                HttpRequest request;
                try {
                    request = requestFactory.buildGetRequest(new GenericUrl("http://localhost:" + portBindings.http + "/a.cfm"));
                    request.execute().disconnect();
                }
                catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });

            final var threadID = DapUtils.doWithStoppedEventFuture(
                dapClient,
                () -> requestThreadToBeBlockedByBreakpoint.start()
            ).get(1000, TimeUnit.MILLISECONDS).getThreadId();

            {
                DapUtils.doWithStoppedEventFuture(
                    dapClient,
                    () -> DapUtils.stepIn(dapServer, threadID)
                ).get(1000, TimeUnit.MILLISECONDS);

                // finally { <<<
                //   0+0;
                // }
                assertEquals(
                    19,
                    DapUtils
                        .getStackTrace(dapServer, threadID)
                        .get(1, TimeUnit.SECONDS)
                        .getStackFrames()[0]
                        .getLine()
                );
            }

            {
                DapUtils.doWithStoppedEventFuture(
                    dapClient,
                    () -> DapUtils.stepIn(dapServer, threadID)
                ).get(1000, TimeUnit.MILLISECONDS);

                // finally {
                //   0+0; <<<
                // }
                assertEquals(
                    20,
                    DapUtils
                        .getStackTrace(dapServer, threadID)
                        .get(1, TimeUnit.SECONDS)
                        .getStackFrames()[0]
                        .getLine()
                );
            }

            {
                DapUtils.doWithStoppedEventFuture(
                    dapClient,
                    () -> DapUtils.stepIn(dapServer, threadID)
                ).get(1000, TimeUnit.MILLISECONDS);

                // catch (any e) { <<<
                //     0+0;
                // }
                assertEquals(
                    6,
                    DapUtils
                        .getStackTrace(dapServer, threadID)
                        .get(1, TimeUnit.SECONDS)
                        .getStackFrames()[0]
                        .getLine()
                );
            }

            {
                DapUtils.doWithStoppedEventFuture(
                    dapClient,
                    () -> DapUtils.stepIn(dapServer, threadID)
                ).get(1000, TimeUnit.MILLISECONDS);

                // catch (any e) {
                //     0+0; <<<
                // }
                assertEquals(
                    7,
                    DapUtils
                        .getStackTrace(dapServer, threadID)
                        .get(1, TimeUnit.SECONDS)
                        .getStackFrames()[0]
                        .getLine()
                );
            }

            DapUtils.disconnect(dapServer).join();
        }
        finally {
            dockerClient.stopContainerCmd(containerID).exec();
            dockerClient.removeContainerCmd(containerID).exec();
        }
    }
}
