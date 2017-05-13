FROM openjdk:8
ADD ./docker-entrypoint.sh /
ADD ./target/datemo-0.1.0-SNAPSHOT-standalone.jar /
RUN /bin/bash -c 'chmod u+x /docker-entrypoint.sh'
ENV PORT=80
EXPOSE 80
CMD ["sh", "-c", "/docker-entrypoint.sh"]

