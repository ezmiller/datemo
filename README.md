# datemo

## Getting Datomic Server Running

1. Datemo relies on [Datomic Cloud](https://docs.datomic.com/cloud/index.html). In order to run in
even in development you will need to have set up a Datomic Cloud instance (see [here](https://docs.datomic.com/cloud/setting-up.html)).

2. Once you have a Datomic Cloud instance setup on AWS, you will need to run the SOCKS proxy. The script provided by
Datomic is located in the root directory of this repository. Run it as follows:
```
./datomic-socks-proxy -p <aws-region> <datomic cloud stack name>
```

3. Now that the Datomic database should be reachable, you can start the datemo server:

```
lein ring server <port-number>
```

4. (optional) If you want to use the repl to access the db:
```
rlwrap bin/repl
```

## To run Datemo to test Production

Assuming the transactor is already running on AWS:

1) Build the jar files with:
```
lein ring uberjar
```

2) Run the following command, adding the AWS keys:
```
java -Ddatabase.db-name="production" -jar target/datemo-<version#>-standalone.jar
```

## Notes on Deployment

Currently, I'm running this instance using Docker and AWS. So the deployment has two steps:
1. Deploy the datemo server (its clojure jar file) and run in connection with a web server
using Docker.
2. Deploy a transactor to AWS using the Datomic tools for deploying via Cloud Formation.

### Deploying the Datemo Server

The Datemo server is currently deployed using the a Docker stack. See the `docker-compose.yml` file
in the project root for the configuration. 

To start the stack you first need to set up the swarm, if it is not already initialized:

```
docker swarm init --advertise-addr "<ip of host>"
```

Then to run the containers on the stack, we can use the config specified in `docker-compose.yml`:

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
3. Update the .jar file specified in the `Dockerfile` and in `docker-entrypoint.sh`.
4. Build the new docker image:
    ```
    docker build --rm -t ezmiller/datemo:latest -t ezmiller/datemo:<version> .
    ```
5. Push both latest and the new version to the Docker Hub:
    ```
    docker push ezmiller/datemo:lates
    docker push ezmiller/datemo:<version>
    ```
6. Now you can do `docker pull` of latest on server and redeploy stack. E.g.:
```
docker pull ezmiller/datemo:latest
```


