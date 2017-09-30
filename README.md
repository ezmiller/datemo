# datemo

## Getting Datomic Server Running

In order to run a simple database for experimental develpoment, where it isn't
necessary to persist the data, do this:

```
bin/run -m datomic.peer-server -p 8998 -a datemo,datemo -d datemo,datomic:mem://datemo
```

In order to get the dev db running, you need to run the following commands from the root of the datomic download package:

1) Run transactor:

```
bin/transactor ~/datemo/resources/dev-transactor.properties
```

3) (optional) If you want the console:
```
bin/console -p 8080 datemo datomic:dev://localhost:4334/datemo
```

4) (optional) If you want to use the repl to access the db:
```
rlwrap bin/repl
```

To run the server do:

```
lein ring server <desired-port-#>
```

Note: You can also run in dev and connect to a transactor on AWS by replacing the database URI
for dev in the project.clj. This is also useful for updating the schema on an AWS deployed DB:

1) Change the database URI in project.clj
2) Run the repl with `lein repl`
3) Do the following in the repl:
```
(use 'datemo.db)
(require '[datomic.api :as d])
(d/delete-database "<database-uri>")
(d/create-database "<database-uri>")
(init-db)
(-> (load-schema "schemas/arb.edn")
    (install-schema (get-conn)))
```

## To run Datemo to test Production

Assuming the transactor is already running on AWS:

1) Build the jar files with:
```
lein ring uberjar
```

2) Run the following command, adding the AWS keys:
```
java -Ddatabase.uri="datomic:ddb://us-east-1/datemo/datemo?aws_access_key_id=<fill-in-key>&aws_secret_key=<fill-in-key>" -jar target/datemo-<version#>-standalone.jar
```

## Notes on Deployment

Currently, I'm running this instance using Docker and AWS. So the deployment has two steps:
1. Deploy the datemo server (its clojure jar file) and run in connection with a web server
using Docker.
2. Deploy a transactor to AWS using the Datomic tools for deploying via Cloud Formation.

### Deploying the Datemo Server

The Datemo server is currently deployed using the a Docker stack. See the `docker-compose.yml` file
in the project root for the configuration. 

To start the stack do:

```
docker stack up -c docker-compose.yml datemo
```

To stop it:

```
docker stack rm datemo
```

To see what is running in the stack do:

```
docker ps
```

To inspect the logs for one of the processes do:

```
docker logs <process-name, e.g. "datemo_web.1.myrooeb6ifz71p00bebftgof6">
```

**Note:** The `VIRTUAL_HOST` setting in `docker-compose.yml` needs to point to the IP of the
AWS instance where the transactor is running, i.e. the instance that is started when deploying
the cloud formation in step #2.

### Deploying a transactor to AWS

Here's a [video](https://www.youtube.com/watch?v=wG5grJP3jKY) that covers the basic process, and here's a [link](http://docs.datomic.com/aws.html) that does the same. None of this documentation is particularly up to date, but it gives the basic idea.

Here is another overview of the steps needed:

1. Create a cloud formation template `.properties` file. Copy the basic template from the
datomic `/config/samples` folder. Set your instance type (e.g. `c3.large` or `m3.medium`).
A list of possible instances can be found by looking at the JSON template file that is generated
at the end of this process.

2. Create an "ensured" cloud formation propreties file by running `bin/datomic ensure-cf`. This
looks like this:
    ```
    bin/datomic ensure-cf /path/to/your-cf.properties /target/path/to/your-cf-ensured.properties
    ```

3. Create the cloud formation JSON template:
    ```
    bin/datomic create-cf-template /path/to/dynamodb-transactor.properties /path/to/ensured-cf.properties /output/path/to/cf.json
    ```

4. Once these steps have been concluded, you should have a workable cloud formation template file.
To deploy the cloud formation, you can do the following:

    ```
    bin/datomic create-cf-stack us-east-1 DatomicTransactor /path/to/cf.json
    ```

5. If you need to delete the stack, do:

    ```
    bin/datomic delete-cf-stack us-east-1 DatomicTransactor
    ```

## Rebuilding Docker Image for new version

1. Make sure you've updated the version in `project.clj`.
2. Build the new jar files by doing: `lein ring uberjar`.
3. Update the .jar file specified in the `Dockerfile`.
4. Build the new docker image:
    ```
    docker build --rm -t ezmiller/datemo:latest -t ezmiller/datemo:<version> .
    ```
5. Push both latest and the new version to the Docker Hub:
    ```
    docker push ezmiller/datemo:latest
    docker push ezmiller/datemo:<version>
    ```
6. Now you can do `docker pull` of latest on server and redeploy stack.


