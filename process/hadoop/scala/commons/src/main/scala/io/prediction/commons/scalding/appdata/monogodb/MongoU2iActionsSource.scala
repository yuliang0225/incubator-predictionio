package io.prediction.commons.scalding.appdata.mongodb

import com.twitter.scalding._

import cascading.pipe.Pipe
import cascading.flow.FlowDef

import java.util.ArrayList
import java.util.HashMap
import java.util.Date
import java.text.SimpleDateFormat

import com.mongodb.BasicDBList
import com.mongodb.casbah.Imports._

import io.prediction.commons.scalding.MongoSource
import io.prediction.commons.scalding.appdata.U2iActionsSource
import io.prediction.commons.scalding.appdata.U2iActionsSource.FIELD_SYMBOLS

class MongoU2iActionsSource(db: String, host: String, port: Int, appId: Int) extends MongoSource (
    db = db, /*(evalId, testSet) match {
      case (Some(x), Some(false)) => "training_appdata"
      case (Some(x), Some(true)) => "test_appdata"
      case _ => "appdata"
    },*/
    coll = "u2iActions",
    cols = {
      val u2iCols = new ArrayList[String]()
      u2iCols.add("action") // 0
      u2iCols.add("uid") // 1
      u2iCols.add("iid") // 2
      u2iCols.add("t") // 3
      u2iCols.add("v") // 4 optional
      //u2iCols.add("evalid")
      u2iCols.add("appid")
      
      u2iCols
    },
    mappings = {
      val u2iMappings = new HashMap[String, String]()
      
      u2iMappings.put("action", FIELD_SYMBOLS("action").name)
      u2iMappings.put("uid", FIELD_SYMBOLS("uid").name)
      u2iMappings.put("iid", FIELD_SYMBOLS("iid").name)
      u2iMappings.put("t", FIELD_SYMBOLS("t").name)
      u2iMappings.put("v", FIELD_SYMBOLS("v").name)
      //u2iMappings.put("evalid", FIELD_SYMBOLS("evalid").name)
      u2iMappings.put("appid", FIELD_SYMBOLS("appid").name)
      
      u2iMappings
    },
    query = { // read query
      /*
      val builder = MongoDBObject.newBuilder
      
      queryData foreach {
        case (field, value) => 
          builder += field -> value
      }
      
      builder.result
      */
      
      val u2iQuery = MongoDBObject("appid" -> appId)
      //++
      //  (evalId.map(x => MongoDBObject("evalid" -> x)).getOrElse(MongoDBObject()))
      
      u2iQuery
    },
    host = host, // String
    port = port // Int
    ) with U2iActionsSource {
  
  import com.twitter.scalding.Dsl._ // get all the fancy implicit conversions that define the DSL
  
  override def getSource: Source = this
  
  /**
   * NOTE: 
   * for optional field vField, due to the current limitation/issue of mongo-hadoop/cascading-mongo Tap,
   * the value will be the same as previous read record if this record has this field missing while
   * None/Null should be expected.
   * Since the meaning of v field depends on action field while action field is a required field,
   * can still work around this issue because can decode meaning of v field based on action field.
   */
  override def readData(actionField: Symbol, uidField: Symbol, iidField: Symbol, tField: Symbol, vField: Symbol)(implicit fd: FlowDef): Pipe = {
    val u2iactions = this.read
      .mapTo((0, 1, 2, 3, 4) -> (actionField, uidField, iidField, tField, vField)) { 
        fields: (String, String, String, String, String) => 
          val (action, uid, iid, t, v) = fields
          
          // convert t to long
          // NOTE: when read from MongoSource, the mongo date object becomes string, eg
          //          Thu Nov 08 13:33:39 CST 2012
          //  format: EEE MMM dd HH:mm:ss zzz yyyy
          // use SimpleDateFormat to parse this date string and and convert back to number in ms unit
          // so later can easily sort it to pick the latest action
   
          val parserSDF: SimpleDateFormat = new SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy");
          val myDate: Date = parserSDF.parse(t);
          val tms: Long = myDate.getTime() // time in millisecond unit 
          
          (action, uid, iid, tms.toString, v)
      }
    
    u2iactions
  }
 
  override def writeData(actionField: Symbol, uidField: Symbol, iidField: Symbol, tField: Symbol, vField: Symbol, appid: Int)(p: Pipe)(implicit fd: FlowDef): Pipe = {
    val dbData = p.mapTo((actionField, uidField, iidField, tField, vField) ->
      (FIELD_SYMBOLS("action"), FIELD_SYMBOLS("uid"), FIELD_SYMBOLS("iid"), FIELD_SYMBOLS("t"), FIELD_SYMBOLS("v"), FIELD_SYMBOLS("appid"))) {
        fields: (String, String, String, String, String) =>
          val (action, uid, iid, t, v) = fields
                    
          (action.toInt, uid, iid, new Date(t.toLong), v.toInt, appid)
    }.write(this)
    
    dbData
  }
  
}
