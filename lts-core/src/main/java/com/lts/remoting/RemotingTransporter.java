package com.lts.remoting;

import com.lts.core.spi.SPI;
import com.lts.core.spi.SpiExtensionKey;

/**
 * @author Robert HG (254963746@qq.com) on 11/6/15.
 */
@SPI(key = SpiExtensionKey.REMOTING, dftValue = "netty")
public interface RemotingTransporter {

    RemotingServer getRemotingServer(RemotingServerConfig remotingServerConfig);

    RemotingClient getRemotingClient(RemotingClientConfig remotingClientConfig);

}
