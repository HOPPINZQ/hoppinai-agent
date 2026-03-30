/*
SQLyog Community v13.1.6 (64 bit)
MySQL - 8.0.24 : Database - main
*********************************************************************
*/

/*!40101 SET NAMES utf8 */;

/*!40101 SET SQL_MODE=''*/;

/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
CREATE DATABASE /*!32312 IF NOT EXISTS*/`main` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `main`;

/*Table structure for table `buff_api_retry_record` */

DROP TABLE IF EXISTS `buff_api_retry_record`;

CREATE TABLE `buff_api_retry_record` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `url` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '请求URL',
  `request_method` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '请求方法(GET/POST等)',
  `request_params` text COLLATE utf8mb4_unicode_ci COMMENT '请求参数(JSON格式)',
  `request_body` text COLLATE utf8mb4_unicode_ci COMMENT '请求体(JSON格式)',
  `http_status_code` int DEFAULT NULL COMMENT 'HTTP状态码',
  `error_code` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '业务错误码',
  `error_message` text COLLATE utf8mb4_unicode_ci COMMENT '错误信息',
  `retry_count` int DEFAULT '0' COMMENT '重试次数',
  `exception_type` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '异常类型',
  `exception_message` text COLLATE utf8mb4_unicode_ci COMMENT '异常消息',
  `status` tinyint DEFAULT '0' COMMENT '状态: 0-待处理, 1-已处理',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_url` (`url`(100)),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=8190 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='BUFF API重试失败记录表';

/*Table structure for table `buff_bill_order` */

DROP TABLE IF EXISTS `buff_bill_order`;

CREATE TABLE `buff_bill_order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `goods_id` int NOT NULL COMMENT 'BUFF物品ID',
  `price` decimal(10,2) DEFAULT NULL COMMENT '交易价格',
  `fee` decimal(10,2) DEFAULT NULL COMMENT '手续费',
  `income` decimal(10,2) DEFAULT NULL COMMENT '实际收入',
  `transact_time` datetime DEFAULT NULL COMMENT '交易时间',
  `buyer_id` varchar(64) DEFAULT NULL COMMENT '买家ID',
  `seller_id` varchar(64) DEFAULT NULL COMMENT '卖家ID',
  `type` int DEFAULT NULL COMMENT '交易类型',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_goods_id` (`goods_id`)
) ENGINE=InnoDB AUTO_INCREMENT=41 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='BUFF物品交易记录表';

/*Table structure for table `buff_goods` */

DROP TABLE IF EXISTS `buff_goods`;

CREATE TABLE `buff_goods` (
  `id` bigint NOT NULL COMMENT 'BUFF物品ID',
  `name` varchar(255) DEFAULT NULL COMMENT '物品名称',
  `market_hash_name` varchar(255) DEFAULT NULL COMMENT '市场Hash名称',
  `short_name` varchar(255) DEFAULT NULL COMMENT '短名称',
  `info` longtext COMMENT '物品信息',
  `icon_url` varchar(500) DEFAULT NULL COMMENT '图标URL',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_goods_id` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='BUFF物品表';

/*Table structure for table `buff_price` */

DROP TABLE IF EXISTS `buff_price`;

CREATE TABLE `buff_price` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `goods_id` int NOT NULL COMMENT 'buff物品ID',
  `sell_min_price` decimal(10,2) DEFAULT NULL COMMENT '最低售价',
  `buy_max_price` decimal(10,2) DEFAULT NULL COMMENT '最高求购价',
  `sell_reference_price` decimal(10,2) DEFAULT NULL COMMENT '参考售价',
  `quick_price` decimal(10,2) DEFAULT NULL COMMENT '快速价格',
  `market_min_price` decimal(10,2) DEFAULT NULL COMMENT '市场最低价格',
  `min_rent_unit_price` decimal(10,2) DEFAULT NULL COMMENT '最低出租单价',
  `rent_unit_reference_price` decimal(10,2) DEFAULT NULL COMMENT '出租参考单价',
  `steam_price_cny` decimal(10,2) DEFAULT NULL COMMENT 'Steam最低价格（CN）',
  `min_security_price` decimal(10,2) DEFAULT NULL COMMENT '最低担保价格',
  `sell_num` int DEFAULT NULL COMMENT '在售数量',
  `buy_num` int DEFAULT NULL COMMENT '求购数量',
  `rent_num` int DEFAULT NULL COMMENT '出租数量',
  `transacted_num` int DEFAULT NULL COMMENT '成交数量',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=33681 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='BUFF物品价格记录表';

/*Table structure for table `buff_price_history` */

DROP TABLE IF EXISTS `buff_price_history`;

CREATE TABLE `buff_price_history` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `goods_id` int NOT NULL COMMENT 'BUFF物品ID',
  `price_type` varchar(64) DEFAULT NULL COMMENT '价格类型: sell_min_price_history(在售最低), sell_price_history(成交记录), goods_existence_history(存世量)',
  `price` decimal(10,2) DEFAULT NULL COMMENT '价格值',
  `quantity` bigint DEFAULT NULL COMMENT '数量值(用于存世量等)',
  `currency` varchar(32) DEFAULT NULL COMMENT '货币类型',
  `timestamp` bigint DEFAULT NULL COMMENT '时间戳(毫秒)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_goods_type_time` (`goods_id`,`price_type`,`timestamp`),
  KEY `idx_goods_id` (`goods_id`),
  KEY `idx_price_type` (`price_type`),
  KEY `idx_timestamp` (`timestamp`)
) ENGINE=InnoDB AUTO_INCREMENT=228776 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='BUFF物品价格历史曲线表';

/*Table structure for table `chat_message` */

DROP TABLE IF EXISTS `chat_message`;

CREATE TABLE `chat_message` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL DEFAULT '1',
  `session_id` varchar(100) NOT NULL,
  `role` varchar(50) NOT NULL,
  `token` int DEFAULT NULL COMMENT '本次对话token消耗',
  `content` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci,
  `message_order` int NOT NULL DEFAULT '1',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_session_id` (`session_id`),
  KEY `idx_message_order` (`session_id`,`message_order`)
) ENGINE=InnoDB AUTO_INCREMENT=39 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

/*Table structure for table `chat_session` */

DROP TABLE IF EXISTS `chat_session`;

CREATE TABLE `chat_session` (
  `session_id` varchar(100) NOT NULL,
  `user_id` bigint NOT NULL DEFAULT '1',
  `title` varchar(255) DEFAULT '新会话',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;
