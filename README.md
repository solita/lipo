# LIPO

LIPO is a LIghtweight POrtal


## Local dev

Before:
- run `npm install`
- create "lipo" database in local postgres
- copy config.sample.edn to config.edn and customize according to comments

To start server call `lipo.main/start` function in REPL.

Watch style changes with:
```$ npm run tailwind```

Open http://localhost:3000/

## Dependencies

Content editor component in the portal requires CKEditor 5, which is not bundled in this repository.
Create your own minimized build and copy it `resources/public/ckeditor.js`.


## Seeding environment with admin commands

You can invoke admin commands to PUT and GET documents via HTTP.
This is useful for seeding an empty environment with some documents
via scripting (eg. curl) without needing direct access to the database.

The system needs to be started with the `LIPO_ADMIN_TOKEN` environment
variable to enable admin routes.

Sending a document:
```
curl -X PUT \
        -H "Authorization: Bearer $LIPO_ADMIN_TOKEN"
        -H "Content-Type: text/plain" \
        http://<host>:<port>/_put/<uuid-of-doc> \
        --data-binary '{:content/path "mydoc" :content/title "This is my doc" :content/body "yes it is"}'
```

Fetching a document:
```
curl -H "Authorization: Bearer $LIPO_ADMIN_TOKEN" \
     http://<host>:<port>/_get/<uuid-of-doc>
```

## Connecting to REPL in cloud

You can connect to an existing environment running in ECS with the following command:

```
% copilot svc exec -c "tmux -S /tmp/lipo-tmux attach"
```
(detach with `Ctrl-b d`)
