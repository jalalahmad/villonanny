                              Travian VilloNanny

                    Dependable, Trustworthy, Caring, Free

Travian is a browser game of farming and combat. It is played in realtime,
24 hours a day, and although most actions take minutes or hours to complete,
you soon become addicted and spend your whole life, days AND NIGHTS, growing
fields, trading goods and moving troups. Soon your life gets fucked up worse
than if you were a drug addict.

Playing Travian, after all, is much like raising a baby: you must perform daily
tasks that will make your creature grow bigger, stronger and respected, losing
lots of sleep in the process. But if you also want to keep living a proper life,
then you need a Nanny.

VilloNanny is the Travian Nanny: it will perform your duties while you work,
eat, sleep, ... All you need is a computer and an internet connection. Any
computer will do (Windows, Apple, Linux, ...) as long as it can run the Java
platform. It doesn't have to be much powerful, and you don't even need a monitor.

== Compile ==

Dependencies :
 - commons-codec-1.3.jar
 - commons-collections-3.1.jar
 - commons-configuration-1.5.jar
 - commons-httpclient-3.1-rc1.jar
 - commons-jxpath-1.2.jar
 - commons-lang-2.2.jar
 - commons-logging.jar
 - junit.jar
 - log4j-1.2.14.jar
 - slf4j-api-1.5.2.jar
 - slf4j-log4j12-1.5.2.jar

=== Install ===

extract the zip file.

=== Configure ===

When you first run VilloNanny, if it doesn't find the configuration file "config/configuration.xml" it
will run the autoconfiguration wizard that will help you generate an initial configuration.
You will need to provide the login address, username and password and the wizard will fetch all
server details and compile a configuration file for you.

You can configure VilloNanny manually if so prefer.
What follows is an example configuration:

<configuration>
    <server desc="Travian comx" enabled="true" language="en" tribe="romans" version="v35b">
        <loginUrl>http://speed.travian.com</loginUrl>
        <user>myuser</user>
        <password>mypwd</password>
        <village desc="my village" enabled="true" uid="v43048731">
            <url>http://speed.travian.com/dorf1.php</url>
            <strategy class="FieldGrowth" desc="Grow Cheapest Field" enabled="true" uid="sfg01"/>
            <strategy class="GrowItem" desc="Grow Main Building" enabled="true" uid="sgi01">
                <item desc="Main Building" id="26" maxLevel="10"/>
            </strategy>
        </village>
    </server>
</configuration>

=== Launch ===

In Windows O.S.
  - launch the startVilloNanny.bat

In Unix O.S. (e.g. Linux)
  - startVilloNanny.sh
  - startVilloNanny_screen.sh (with a screen session CTRL-A CTRL-D for detach)

=== Console ===

VilloNanny supports a console with limited functionality. Press "Enter" to enter console
mode. You can then pause, restart, quit, etc.
For a list of commands type "help" (no quotes) and press enter.

=== More Info ===

Please visit the web site at http://www.villonanny.net


