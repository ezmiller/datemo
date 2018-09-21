FROM openjdk:8
ADD ./docker-entrypoint.sh /
RUN /bin/bash -c 'chmod u+x /docker-entrypoint.sh'
ADD ./target/datemo-0.2-standalone.jar /
ENV PORT=8080
EXPOSE 8080
CMD ["sh", "-c", "/docker-entrypoint.sh"]

