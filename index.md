---
title: "Overview"
addon: "JMS"
repo: "https://github.com/seedstack/jms-addon"
author: "SeedStack"
description: "Provides configuration, injection and connection resilience for Java Messaging System 1.1."
min-version: "15.7+"
menu:
    AddonJMS:
        weight: 10
---

Java Message Service (JMS) is a Java API that allows applications to create, send, receive, and read messages.
This add-on provides a JMS 1.1 integration (a.k.a. JSR 914). It automatically manages connection factories,
connections, sessions and message consumers/listeners while retaining the standard JMS API. Moreover connection
and session try to reconnect automatically after a JMS connection failure.

{{% callout info %}}
JMS provider implementation is not provided by this add-on and must be configured depending on your messaging solution.
{{% /callout %}}

{{< dependency "org.seedstack.addons" "jms-core" >}}

The JMS specification jar dependency is required as well since Seed JMS support doesn't transitively provide this 
dependency:

    <dependency>
        <groupId>javax.jms</groupId>
        <artifactId>jms-api</artifactId>
        <version>1.1-rev-1</version>
        <scope>provided</scope>
    </dependency>
