target: all

ROUTER_APP := vrouter

all: app

app:
	mvn clean install -DskipTests -f "$(ROUTER_APP)/pom.xml"

clean:
	mvn clean -f "$(ROUTER_APP)/pom.xml"
	
