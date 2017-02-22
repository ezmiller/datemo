# datemo

A Clojure library designed to ... well, that part is up to you.

## Getting Datomic Server Running

In order to run a simple database for experimental develpoment, where it isn't
necessary to persist the data, do this:

```
bin/run -m datomic.peer-server -p 8998 -a datemo,datemo -d datemo,datomic:mem://datemo
```

In order to get the db running, you need to run the following commands from the root of the datomic download package:

1) Run transactor:

```
/datomic/datomic-pro-0.9.5544/bin/transactor /path/to/transactor/config
```

2) Run peerserver:
```
bin/run -m datomic.peer-server -p 8998 -a datemo,datemo -d datemo,datomic:dev://localhost:4334/datemo
``

3) (optional) If you want the console:
```
bin/console -p 8080 datemo datomic:dev://localhost:4334/datemo
```

## License

Copyright © 2017 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
