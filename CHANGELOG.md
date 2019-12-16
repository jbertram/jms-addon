# Version 3.1.0 (2019-12-20)

* [new] Support disabling JMS listeners by setting destination type to "DISABLED". This can be substituted with configuration with the `destinationTypeStr` annotation parameter.

# Version 3.0.1 (2017-02-23)

* [fix] JMS sessions were still tracked after being closed leading to trying to reconnect all sessions used in the past.

# Version 3.0.0 (2016-12-13)

* [brk] Update to SeedStack 16.11 new configuration system.
* [chg] Transformed into a single module add-on. 

# Version 2.1.2 (2016-04-26)

* [chg] Update for SeedStack 16.4.
* [fix] Correctly cleanup `ThreadLocal` in `JmsSessionLink`.

# Version 2.1.1 (2016-01-21)

* [fix] Error message were referencing outdated information.

# Version 2.1.0 (2015-11-26)

* [chg] Refactored as an add-on and updated to work with Seed 2.1.0+

# Version 2.0.0 (2015-07-30)

* [new] Initial Open-Source release.
