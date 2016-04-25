/*
 * Copyright (c) 2015-2016, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package okapi.web;

import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import okapi.bean.DeploymentDescriptor;
import okapi.deployment.DeploymentManager;
import static okapi.util.ErrorType.*;
import static okapi.util.HttpResponse.responseError;
import static okapi.util.HttpResponse.responseJson;
import static okapi.util.HttpResponse.responseText;

public class DeploymentWebService {
  private final Logger logger = LoggerFactory.getLogger("okapi");
  private final DeploymentManager md;

  public DeploymentWebService(DeploymentManager md) {
    this.md = md;
  }

  public void create(RoutingContext ctx) {
    try {
      final DeploymentDescriptor pmd = Json.decodeValue(ctx.getBodyAsString(),
              DeploymentDescriptor.class);
      md.deploy(pmd, res -> {
        if (res.failed()) {
          if (res.getType() == INTERNAL) {
            responseError(ctx, 500, res.cause());
          } else {
            responseError(ctx, 400, res.cause());
          }
        } else {
          final String s = Json.encodePrettily(res.result());
          responseJson(ctx, 201)
                  .putHeader("Location", ctx.request().uri() + "/" + res.result().getId())
                  .end(s);
        }
      });
    } catch (DecodeException ex) {
      responseError(ctx, 400, ex);
    }
  }

  public void delete(RoutingContext ctx) {
    final String id = ctx.request().getParam("id");
    md.undeploy(id, res -> {
      if (res.failed()) {
        if (res.getType() == NOT_FOUND) {
          responseError(ctx, 404, res.cause());
        } else {
          responseError(ctx, 500, res.cause());
        }
      } else {
        responseText(ctx, 204).end();
      }
    });
  }

  public void list(RoutingContext ctx) {
    md.list(res -> {
      if (res.failed()) {
        responseError(ctx, 500, res.cause());
      } else {
        final String s = Json.encodePrettily(res.result());
        responseText(ctx, 200).end(s);
      }
    });
  }

  public void get(RoutingContext ctx) {
    final String id = ctx.request().getParam("id");
    md.get(id, res -> {
      if (res.failed()) {
        if (res.getType() == NOT_FOUND) {
          responseError(ctx, 404, res.cause());
        } else {
          responseError(ctx, 500, res.cause());
        }
      } else {
        final String s = Json.encodePrettily(res.result());
        responseText(ctx, 200).end(s);
      }
    });
  }
}
