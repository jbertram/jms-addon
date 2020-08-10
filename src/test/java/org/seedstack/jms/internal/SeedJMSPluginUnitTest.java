/*
 * Copyright © 2013-2020, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.jms.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.nuun.kernel.api.plugin.context.InitContext;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.function.Predicate;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.seedstack.coffig.Coffig;
import org.seedstack.jms.JmsConfig;
import org.seedstack.seed.Application;
import org.seedstack.seed.core.internal.jndi.JndiPlugin;
import org.seedstack.seed.core.internal.transaction.TransactionPlugin;
import org.seedstack.seed.spi.ApplicationProvider;

public class SeedJMSPluginUnitTest {
    private JmsPlugin underTest = new JmsPlugin();
    private JmsConfig conf = new JmsConfig();

    @Before
    public void setUp() throws Exception {
        conf.addConnectionFactory("default", new JmsConfig.ConnectionFactoryConfig()
                .setVendorClass(ActiveMQConnectionFactory.class)
                .setVendorProperty("brokerURL", "vm://localhost?broker.persistent=false")
        );
    }

    @Test
    public void testName() {
        assertThat(underTest.name()).isEqualTo("jms");
    }

    @Test
    public void testInit() {
        underTest.init(buildCoherentInitContext());
    }

    @Test
    public void testDependencyInjectionDef() {
        underTest.init(buildCoherentInitContext());
        Object actual = underTest.nativeUnitModule();
        assertThat(actual).isInstanceOf(JmsModule.class);
    }

    @Test
    public void testRequiredPlugins() {
        assertThat(underTest.requiredPlugins()).containsOnly(ApplicationProvider.class, TransactionPlugin.class, JndiPlugin.class);
    }

    @SuppressWarnings("unchecked")
    private InitContext buildCoherentInitContext() {
        InitContext initContext = mock(InitContext.class);

        ApplicationProvider applicationProvider = mock(ApplicationProvider.class);
        Coffig coffig = mock(Coffig.class);
        when(coffig.get(JmsConfig.class)).thenReturn(conf);
        Application application = mock(Application.class);
        when(application.getConfiguration()).thenReturn(coffig);
        when(application.getId()).thenReturn("test-app-id");
        when(applicationProvider.getApplication()).thenReturn(application);

        TransactionPlugin txplugin = mock(TransactionPlugin.class);
        JndiPlugin jndiplugin = mock(JndiPlugin.class);
        when(jndiplugin.getJndiContexts()).thenReturn(new HashMap<>());

        HashMap<Predicate<Class<?>>, Collection<Class<?>>> map = mock(HashMap.class);

        when(initContext.dependency(ApplicationProvider.class)).thenReturn(applicationProvider);
        when(initContext.dependency(TransactionPlugin.class)).thenReturn(txplugin);
        when(initContext.dependency(JndiPlugin.class)).thenReturn(jndiplugin);

        when(initContext.scannedTypesByPredicate()).thenReturn(map);

        when(map.get(Mockito.any(Predicate.class))).thenReturn(Collections.EMPTY_LIST);
        return initContext;

    }
}
