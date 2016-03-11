/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package okapi.service.impl;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.mongo.MongoClient;
import java.util.Iterator;
import java.util.List;
import static okapi.util.ErrorType.INTERNAL;
import okapi.util.ExtendedAsyncResult;
import okapi.util.Failure;
import okapi.util.Success;

/**
 * Generic handle to the Mongo database.
 * Encapsulates the configuration and creation of Mongo client
 * that can be passed on to other Mongo-based storage modules.
 */
public class MongoHandle {
  private final Logger logger = LoggerFactory.getLogger("okapi");
  private final MongoClient cli;
  private final String transientDbName = "mongo_transient_test";
  private boolean transientDb = false;

  // Little helper to get a config value
  // First from System (-D on command line),
  // then from config (from the way the vertcle gets deployed, f.ex. in tests
  // finally a default value.
  static String conf(String key, String def, JsonObject conf,
        JsonObject mongoOpt, String mongoKey) {
    String v = System.getProperty(key, conf.getString(key,def));
    if ( v != null && ! v.isEmpty() )
      mongoOpt.put(mongoKey, v);
    return v;
  }


  public MongoHandle(Vertx vertx, JsonObject conf) {
    JsonObject opt = new JsonObject();
    String h = conf.getString("mongo_host","127.0.0.1");
    if ( ! h.isEmpty() )
      opt.put("host", h);
    String p = conf.getString("mongo_port","27017");
    if ( ! p.isEmpty() )
      opt.put("port",Integer.parseInt(p));
    String db = conf("mongo_db_name", "", conf, opt, "db_name");
    this.cli = MongoClient.createShared(vertx, opt);
    if ( transientDbName.equals(db)) {
      logger.debug("Mongohandle: Decided that this a transient backend!");
      this.transientDb = true;
    }
  }
 
  public MongoClient getClient() {
    return cli;
  }

  public boolean isTransient() {
    return this.transientDb;
  }
  
  /**
   * Drop all (relevant?) collections.
   * The idea is that we can start our integration tests on a clean slate
   * 
   */
  public void dropDatabase(Handler<ExtendedAsyncResult<Void>> fut) {
    cli.getCollections(res->{
      if ( res.failed())
        fut.handle(new Failure<>(INTERNAL,res.cause()));
      else {
        List<String> collections = res.result();
        Iterator<String> it = collections.iterator();
        dropCollection(it, fut);
      }
    });

  }

  private void dropCollection(Iterator<String> it,
        Handler<ExtendedAsyncResult<Void>> fut) {
    if ( !it.hasNext()) { // all done
      logger.info("Dropped all okapi collections from " + transientDbName);
      fut.handle(new Success<>());
    } else {
      String coll = it.next();
      if (coll.startsWith("okapi")) {
        cli.dropCollection(coll, res->{
          if (res.failed()) {
            fut.handle(new Failure<>(INTERNAL,res.cause()));
          } else {
            logger.debug("Dropped whole collection " + coll);
            dropCollection(it,fut);        
          }
        });
      } else {
        logger.debug("Not dropping collection '" + coll + "'");
        dropCollection(it,fut);
      }
    }
  }

}