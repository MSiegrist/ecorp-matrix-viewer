package app;

import service.BroadcastService;
import service.MasterClientService;
import service.MasterDiscoveryService;
import util.ServiceManager;
import util.SimpleApp;

import java.net.*;
import java.util.ArrayList;
import java.util.List;

public class Main extends SimpleApp {
    /**
     * Options
     */
    private Options options_ = null;

    /**
     * Services
     */
    private ServiceManager services_ = null;

    /**
     * Broadcast
     */
    private BroadcastService broadcast_ = null;

    /**
     * Master discovery
     */
    private MasterDiscoveryService discovery_ = null;

    /**
     * Master services...
     */
    private List<MasterClientService> masters_ = null;

    /**
     * Main master service
     */
    private MasterClientService master_ = null;

    /**
     * Entry point
     * @param args commandline
     */
    public static void main( String[] args) {
        new Main().Run(args);
    }

    /**
     * Get options
     * @return
     */
    @Override
    public Object GetOptions() {
        return options_;
    }

    /**
     * OnLoad
     */
    @Override
    public void OnLoad() {
        // Option handler
        options_ = new Options();

        // Service manager
        services_ = new ServiceManager();

        // Master list
        masters_ = new ArrayList<MasterClientService>();
    }

    /**
     * OnInit
     */
    @Override
    public void OnInit() {
        // Register services
        try {
            RegisterServices();
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    /**
     * Entry point
     */
    @Override
    public void OnApp() {
        // Register threaded services
        services_.Start();

        // Wait handler
        try {
            // Connect (we freeze till we're ready)
            Connect();

            // Clean
            CleanMasters();

            // If master
            if (master_ != null) {
                System.out.println("We're connected");
            }

            // Wait till we exit
            services_.Wait();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Connect to master server
     * @throws InterruptedException on interrupt
     */
    private void Connect() throws InterruptedException {
        // Register
        boolean register;

        // TODO: Remuve debug
        options_.master_ = "127.0.0.1";

        // Append default service
        if (options_.master_.length() > 0) {
            // Master client services...
            final MasterClientService s = new MasterClientService(options_.master_, options_.port_);
            if (options_.ssl_)
                s.InitializeSSL();

            s.Start();

            // Register to possible masters
            masters_.add(s);
        }

        // Connect loop
        while (true) {
            // Register
            register = false;

            // Check if discovery found a new address
            InetAddress addr = discovery_.PopAddress();

            // Check if one of the services is ready
            for (MasterClientService service : masters_) {
                // Addr check
                if (addr != null && !service.GetHost().equals(addr.getHostAddress())) {
                    register = true;
                }

                // If service is ready we quit.
                if (service.Ready()) {
                    // Register main service
                    master_ = service;

                    // Make sure our main service is out of the list
                    masters_.remove(service);
                    return;
                }
            }

            // Register
            if (register) {
                // Register to possibler masters
                final MasterClientService s = new MasterClientService(addr.getHostAddress(), options_.port_);
                if (options_.ssl_)
                    s.InitializeSSL();
                s.Start();
                masters_.add(s);
            }

            // Pause
            Thread.sleep(100);
        }
    }

    /**
     * Quit
     */
    private void CleanMasters() {
        for (MasterClientService service : masters_) {
            service.Interrupt();
        }
        masters_.clear();
    }

    /**
     * Register services
     * @throws SocketException if MasterDiscoveryService isn't ready
     */
    private void RegisterServices() throws SocketException {
        // If we support the discovery service....
        if (!options_.wdc_) {
            // Register broadcast channel
            broadcast_ = new BroadcastService(59607);
            discovery_ = new MasterDiscoveryService(broadcast_);

            // Set discovery service
            broadcast_.SetDiscoveryService(discovery_);

            // Register to main service handler
            services_.Register(broadcast_);
            services_.Register(discovery_);
        }
    }

}