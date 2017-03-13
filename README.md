# datemo

## Getting Datomic Server Running

In order to run a simple database for experimental develpoment, where it isn't
necessary to persist the data, do this:

```
bin/run -m datomic.peer-server -p 8998 -a datemo,datemo -d datemo,datomic:mem://datemo
```

In order to get the db running, you need to run the following commands from the root of the datomic download package:

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

