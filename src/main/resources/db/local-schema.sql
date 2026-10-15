-- Minimal, inferred development schema. NOT a production migration.
CREATE SCHEMA IF NOT EXISTS dbo;
CREATE TABLE IF NOT EXISTS dbo.accounts (
  firmId INT NOT NULL,
  accountId VARCHAR(100) NOT NULL,
  correspondent VARCHAR(100), branch VARCHAR(100), rep VARCHAR(100),
  accountName VARCHAR(255), accountCat VARCHAR(100),
  marginable BIT, aggregate BIT, taxlotMethod VARCHAR(100),
  PRIMARY KEY (firmId, accountId)
);
CREATE TABLE IF NOT EXISTS dbo.accountBook (
  accountBookID INT IDENTITY PRIMARY KEY,
  firmId INT NOT NULL, accountId VARCHAR(100) NOT NULL,
  processDate DATE NOT NULL, bookJson VARCHAR(MAX),
  UNIQUE (firmId, accountId, processDate)
);
CREATE ALIAS IF NOT EXISTS DB_NAME AS 'String databaseName() { return "regulus-local"; }';
