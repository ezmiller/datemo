FROM openjdk:8
ADD ./docker-entrypoint.sh /
ADD ./target/datemo-0.1.1-standalone.jar /
RUN /bin/bash -c 'chmod u+x /docker-entrypoint.sh'
ENV PORT=8080
EXPOSE 8080
CMD ["sh", "-c", "/docker-entrypoint.sh"]

