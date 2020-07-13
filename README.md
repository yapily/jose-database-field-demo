# JOSE database field encryption demo

Demo of the JOSE database field encryption.

It is recommended the you read the three part series which will explains the purpose and background behind this initiative. From now on, it will be assumed that you are familiar with the motivations and  and will concentrate on walking through a live example of how to run the various images created to implememt the database field encryption solution.

## Setting up the tutorial

- Clone this repo. `git clone git@github.com:yapily/jose-database-field-demo.git; cd jose-database-field-demo`

The repo contains all the components you need, especially the [JOSE CLI](https://github.com/yapily/jose-cli) which is in the directory `./jose-cli`.

### Setup the postman collection

The examples in this README will use `curl`, however, there is a Postman collection which is available in the `/postman` directory which you can use to quickly make requests.


## Architecture of this demo

For this demo, we will spin up docker images directly via docker-compose. It's composed of the following components:
- `application-alice`: will play the role of your spring boot app, which you want to access a PostgreSQL database with JOSE formatted field.
- `db`: a simple PostgreSQL database that we will provision with a sample table `person`. You can find the init of the database in `./db/postgres/init.sql`
- `jose-reencrypt-database`: A spin up image of the [JOSE batch](https://github.com/yapily/jose-batch) that will maintain your database field encrypted with the latest keys. 
- `jose-cli`: we will use the CLI to rotate our keys and propagate the changes to our application alice and the database.
- `./keys`: this folder contains the keys we will use for the encryption. Initially, we provided a set with only 2 valid keys: one for signing and one for encrypting.

### Application alice

This application is a sample person directory. It will store a Pojo of a person in a database and offer APIs to retrieve them.

A person will be represented with the following attributes:
- name: the name of the person, that we will keep in clear
- email: the email of the person, that we will want to store encrypted in our database

The application offers some basic REST endpoints:
- `GET /persons/` : Get all the persons. It will decrypt the email on the fly and returns you the decrypted value
- `POST /persons/random?nb-entries=3`: Generate 3 random persons 

In order to help you visualise the actual encryption happening behind the scene, we create an endpoint which returns a person as
they are represented in the database, meaning with the email as a JWT:
- `GET /persons/raw`: returns all the users as they are stored in the DB, in our case with the email as a JWS(JWE)

We will want to see the current status of the database fields, like how many fields are encrypted with which keys.
You could do this manually via the `/persons/raw` endpoint but it can be a very annoying job to do. Instead, we created an endpoint
which would provide a summary of the JOSE database field status:
- `GET /actuator/jose-database` 

As part of the JOSE-database feature, it offers a custom actuator endpoints which present you the current keys from the application angle.
This is quite handy to verify that your application has the right set of keys.
- `GET /database/status?details=true`: show you the status of our table `person` with the details for each row.

### db

The DB would be a PostgreSQL with one table `person`. You can check the table in `./db/postgres/init.sql`

### jose-reencrypt-database

It's our JOSE spring batch that we configured to run against our PostgreSQL database, on the table `person` for the field `email`.
It's a job, meaning that once it's done re-encrypted the field, it will shut down. In this tutorial, we will run the job each time we do a new docker-compose.

### `jose-cli`
The JOSE CLI we uploaded to this repo was the latest at the time we write this line. You will need to have Java setup on your laptop first.
You can verify you got the CLI in good shape by doing:

```
 ./jose-cli/jose --version
"2020-07-10 14:23:56.540  INFO : Starting JoseCLIRunner on quentins-MacBook-Pro-2.local with PID 25757 (/Users/quentincastel/Development/GIT/github/yapily/jose-database-field-demo/jose-cli/jose-cli.jar started by quentincastel in /Users/quentincastel/Development/GIT/github/yapily/jose-database-field-demo)
"2020-07-10 14:23:56.543 DEBUG : Running with Spring Boot v2.2.2.RELEASE, Spring v5.2.2.RELEASE
"2020-07-10 14:23:56.543  INFO : The following profiles are active: cli
"2020-07-10 14:23:57.470  INFO : Started JoseCLIRunner in 1.75 seconds (JVM running for 2.818)
```
### ./keys

We generate a set of keys to help you consume this repository. They are stored under `./keys`. If you want to create a new set of keys,
you can use the CLI: `./jose-cli/jose jwks-sets init -o ./keys` 


## Demo

### Step 1: Run the services and check the initial state

#### Run the services
We provided a docker compose. All you need to do is:

```
docker-compose up
```

This will spin up our 3 docker images. You can verify that you got them up and running via a docker ps
```
❯ docker ps
CONTAINER ID        IMAGE                                        COMMAND                  CREATED             STATUS              PORTS                    NAMES
237c05591ac0        jose-database-field-demo_alice-application   "sh -c 'java $JAVA_O…"   31 seconds ago      Up 19 seconds       0.0.0.0:8080->8080/tcp   alice-application
41fba9afaae8        postgres:9.6.9                               "docker-entrypoint.s…"   31 seconds ago      Up 30 seconds       0.0.0.0:5435->5432/tcp   postgres_jose_database_example
fd5505662230        yapily/jose-batch:latest                     "java -cp /app/resou…"   31 seconds ago      Up 30 seconds                                jose-reencrypt-database
```

Note that if the container `jose-reencrypt-database` has already completed, it may already be shutdown. In that case, don't be surprise if you don't see it:

```
❯ docker ps
CONTAINER ID        IMAGE                                        COMMAND                  CREATED             STATUS              PORTS                    NAMES
237c05591ac0        jose-database-field-demo_alice-application   "sh -c 'java $JAVA_O…"   5 minutes ago       Up 5 minutes        0.0.0.0:8080->8080/tcp   alice-application
41fba9afaae8        postgres:9.6.9                               "docker-entrypoint.s…"   5 minutes ago       Up 5 minutes        0.0.0.0:5435->5432/tcp   postgres_jose_database_example
```
#### Verify the initial the state

You should be able to check the service is up and running by calling the database status endpoints:

```
curl --location --request GET 'http://localhost:8080/database/status'
```

```
Status of the progress database at 2020-07-10 13:29:26
- Nb of raws: 0
-------------
Number of entries by key type:
- REVOKED : 0 ;
- EXPIRED : 0 ;
- VALID : 0 ;
```

As you can see for now, we haven't got any person stored in the database.
Let's generate 3 persons:

```
curl --location --request POST 'http://localhost:8080/persons/random?nb-entries=3'
```

```
[
  {
    "id": 1,
    "name": "dHAoGKOKuf",
    "email": "dHAoGKOKuf@yapily.com"
  },
  {
    "id": 2,
    "name": "WZEmygOYHI",
    "email": "WZEmygOYHI@yapily.com"
  },
  {
    "id": 3,
    "name": "OvoMLKPHVK",
    "email": "OvoMLKPHVK@yapily.com"
  }
]
```


Now lets check what our database status is saying:

```
curl --location --request GET 'http://localhost:8080/database/status'
```

```
Status of the progress database at 2020-07-10 13:34:41
- Nb of raws: 3
-------------
Number of entries by key type:
- REVOKED : 0 ;
- EXPIRED : 0 ;
- VALID : 3 ;
-------------
Number of JWTs using keys:
- 4ef01c58-8b45-4874-bdb0-864eb5ef7af6 : 3 ;
- ed24f836-5e22-4e0c-b9b2-dc4ee85128a9 : 3 ;
```

You can see that we got now 3 database rows and each of them got the `email` field encrypted with a set of valid keys.
You see two keys `4ef01c58-8b45-4874-bdb0-864eb5ef7af6` and `ed24f836-5e22-4e0c-b9b2-dc4ee85128a9`, this is because the email is formatted as JWS_JWE.
Meaning that we first encrypt the email as JWE using the key `ed24f836-5e22-4e0c-b9b2-dc4ee85128a9` and then we sign this JWE as a JWS, using the signing key `4ef01c58-8b45-4874-bdb0-864eb5ef7af6`.



Let's check now that our `GET /persons/` is able to read those persons and decrypt on the fly the emails.

```
curl --location --request GET 'http://localhost:8080/persons/'
```

```
[
    {
        "id": 1,
        "name": "UAjKogHhAa",
        "email": "UAjKogHhAa@yapily.com"
    },
    {
        "id": 2,
        "name": "SkhstwfFQT",
        "email": "SkhstwfFQT@yapily.com"
    },
    {
        "id": 3,
        "name": "SMGwETaAQY",
        "email": "SMGwETaAQY@yapily.com"
    }
]
```

Success! Our application is indeed decrypting the email on the fly!

To convinced you that they were encrypted in the first place, let's check our raw endpoints:

```
curl --location --request GET 'http://localhost:8080/persons/raw'
```

```
[
    {
        "id": 1,
        "name": "UAjKogHhAa",
        "email": "eyJraWQiOiI0ZWYwMWM1OC04YjQ1LTQ4NzQtYmRiMC04NjRlYjVlZjdhZjYiLCJhbGciOiJQUzUxMiJ9.ZXlKcmFXUWlPaUpsWkRJMFpqZ3pOaTAxWlRJeUxUUmxNR010WWpsaU1pMWtZelJsWlRnMU1USTRZVGtpTENKbGJtTWlPaUpCTWpVMlEwSkRMVWhUTlRFeUlpd2lZV3huSWpvaVVsTkJMVTlCUlZBdE1qVTJJbjAuSjJkVHJoYzlOWFF3SGJncTE4cjliRFNKbXNHUk9FTUJGa3RXWnh5TThXV0VCdUNRV29JaDRuNnZDMG5uU3dIUFh6VXB0OTZJNnpfeDcwZzlCYklDT040RFZ4QnRHM3JiQ1ZwV0UwV1dZNE1vZnZ5NU5ZRTlCS1ktYlZrc3JuZ2FkU2o3NFFSTEl5U1ZPQVBzTlpYS1VfVEFQM2xVcWktZ1V0Q1pMZFRrSHFzM1luM2NnV1VlUktNY0gyQWEyRzY4aE9QY19fd1lDTmpIMHZKZFR1dWcyWW03RUpkdm84U3dneHdiRjg3eERJOG52TjFfdFBBV19La2g4R0ZCd0N2XzJHNjlFaUpRSHlNdXhyaXlrUXpoR3BZWWRDVTdFNzN2M0NEUTIweXNoLXJmT0kyVzZjSTdubDlqc3Q2dVhIeDl5ZGhMbU5fNk5sZXZQbG9ZdzdHVHpRU1V2MmtpZ3lPbXFBLUh5VlBrbXo3Ri00ZGR0VFBBWGpiYWNLalRMS2tBTXhJeElXRm02bWc1YjJOdnZGZ040TXZxaTZiTGUxY09MaFhRczg5RGtXeTgyc05aRWFINEhxTVBhV0k1RWNqUTc5SElLbW5VR0lfNnR6Y2oyTF9OZTRNX19Sd3RRZ0Q0d0Z1WkZndTRWRWpNZ1Z5LVdXUnduOEZkeVJoX3RYUGtwRjZzNzFQXzVXNTdyNXpmQlJiMjNaNXhMbnNVbXpFbWZ1ekliUjZ4enRsRmRMbDJ3TFJfWmlueGREamRaeHllbzc3RWJSaUU0YTV5NFJzdGtoT21KdjhUTy1QZTlmM2FIeUJsazJZMXRWbmdnU1NPcXYwdWZNRjNkdzYxZU1nb2tTRkQ5QTd1R3BtdlN1aDFzY0oxa0ptZTU2ejd5OVBJSHFhZG5OZ2h1em8uYmdRR1Z1YUtqS3MzVDlyVDdUaERDdy5ENTB0Q1hPT0c1VzgyODBVcV9HWXpNamZyOUJEWk1fLW1QU1ZtWWVPWnFvLkY1RUtqWFZDaF9FYjBnblBsT3piZGdqbENrdm1oY3ZYQW5aeVAzY3VJVFk.lOoBDOuqJSTgYopneMW34FwLniXztV46VHq3v2eDxUYr7TPhHK6HglO8YNZECZsgSYcMifaeBgDniSNPj-QlxnkXwvPp3liJ9zOYSJaEvHq7hbKaZyxW2BwDERI90h-XYM3agGed9WSSAR_0_oMGDLZCxBiBfHahE0pRDHFS0nSj_yxiDyXsC23dy0oXo3rXRp8EvChK5xAV2_SaKiyPbJJZocrkYqoQXt21rZ-CSH46sVCT1Clj_IVvTiWSHw8n-nooSo6V8UtEnGRmFwJAZQgOqwPzL9dcn4ZAz9feb3HIDYmDHqOR5b4fjse2z1igat2sh5k2mFFXKKR7k_PFax2ALFId33zLtdguucRRw7aZcRB-hIpn0cgN9D7EYoPGIJt4O5PJGGx5b_srqgtOGA5HF7A2WLbocnzMp4myyPI402jsTLMVp-xiKgHw5EEILrmJ0GJ1xbp38aZKy-8v6_2bEmBdGwnfYELpv8hAKKf8EjCFlb1u_IrBxfcrcfYWCqS8eBHd9KEReh8EMy3qmcnIpOgjNyAI8XCpKMerUmGl4EKSXIxB7Oin65ArZ2tbnwufl_2MOlMk1odXzbmGKnxH9WYKh058zmNengGvq4xoWnA9uhT1iNpuRWWnRSQQHaY87GtVh04cUy5w9lfcb-2WLaHD_7FZxS5Rune0uNk"
    },
    {
        "id": 2,
        "name": "SkhstwfFQT",
        "email": "eyJraWQiOiI0ZWYwMWM1OC04YjQ1LTQ4NzQtYmRiMC04NjRlYjVlZjdhZjYiLCJhbGciOiJQUzUxMiJ9.ZXlKcmFXUWlPaUpsWkRJMFpqZ3pOaTAxWlRJeUxUUmxNR010WWpsaU1pMWtZelJsWlRnMU1USTRZVGtpTENKbGJtTWlPaUpCTWpVMlEwSkRMVWhUTlRFeUlpd2lZV3huSWpvaVVsTkJMVTlCUlZBdE1qVTJJbjAuSDVNSmNqRzk3Nm9KQ21HVEVNdXBJdHRCLXRORUdqaEphMFJyVmg3Q1RHU0hQdDljVFBaMkdFdmVsd1VacEtVcmYtQ2lfNjhtOGNsSVRBRC1VTXdwUkNtaTBHczAwSnpwN053dXJIQy1BLXVZM041LW40U1l5YjI2UG5xMjhRall0N1Z6Y3F3QjZNWHd5bjl6TjRvT3hkd3hwMzVhWlU4SjQ5OGRyUVdSVzFISDBtU0ZOSDF1N2tsZWFzRlp0cE80QjVvZlg2NTdHSUpxWDQtVk9Rd2N4UTZhWU1IRF9HUGIwNFFwdGtNdUFBMXdWRUVzbjJuZUg2ZExRNkFyYTJzSFZDQTdLVmZ4Z0d0VmRlNmdWWlk3UW5vRFBnUTdoN21HQ3plYjNGcHVCNmc2UldxYmNjdlNlYzhEMjRHSGJ2ZzlaVXRlczJNN3J6THFHOHB0VGNTVE4yUElISDJjdnFmN1UtQXA0VUhLX0Z4VFhaZmZMN01abDd5QzVXbFJkTXJoTjBMQ29sTmtObkJKYS1XUmxMYW5qd2VuTDR1dXBCLWUtejlGWEFIVVRaRGt1LTJncFRlaWx6Mnd2SmxzMXlXZTBRcUJzWFFIeVFiYU13YzMzeGdQcGIwQlpZU09EQmxZZ0hpcDE5d3NHQndabzdXWkZZSzVwTVNhZnpWTmZlbXRoWTdnOWQ2aW5JX2hzRkUwMC1pWVhEaUxtdTRBVWJMSDVfMHpzNXdkbzN3Ulp1M3E4LV9KMkk2ckZxY0o2VUttekFqblhPSWJFM25RTTZ6Sk4tNVRiQWdONWdDR25tbXlmM2tzSF9VM0xSamUyclFuWHYtOThKME5mVUthaVJRZHBNWW5RTUVfOUVpWEh4MXRQcjZldEZYZVhHa1VkMVJIa3poZGE0dEZWR1EuRnJSZnNSMXBmamVuWTBkcXU5MUt2US4zRVZjQzROSWpabmxrLXZjczctTW1FNkU0eHdYSXVfek5ZMmhjdlh5UHBNLm1WU3RkQnlBcVIwR0Q3b1JkQUNCUnVVVzdTcTVxM3VhcWZOdW96OWNoR0k.b0pVf0Rgn1s91FtO0QE2Hz45TiRRWTp4zhknUDuVCGaLFjQksiHgrlw4HBcopgzHkClrmY18FMhaaqxFP_O1asYFrhleYHezsoxw4i4RIx1RY0Pupi34nCWhe0KvtoCV004Dye7pSMPoSE0Svp3KclwPvpaUbVEcOWIfuANOf7astjCHfwUFpLTc19PiGTwD1_3-P_IHq3lvwCRnXEj0SXYeM-_Pt7A8gGeFjnQpBHWPE1F77U-GgcUQ1ZjrZYqK2eBbAlPRjk84pp6P0SD9A-S9hftKTaRfVNzkTu4VfDtDKneF-2T21N2LJU5GMVhdPGEhnPnQkX-2nHfcldP5dDBUeq3JuQQRYt235kY_eUUcgPS9A0G9Mi1ZnkTuVPa4PRx3K1j5n6okMPbTHRIxYgtgYMMrwkDWN0drX5dGodzaXBTnXs91QxJ7ogh36ewqA6R1IbVpVl5we1TMNhJwCYVqWyE10dpxMwavW533C0kcsxdZyriCj3nxBVDLns3XJLvH1D4gxibp_DHG_Gf2KQ9R9ruZusLzRwrBVIIzlTz-k1UqzQ5YUCfYqgIOcm6-l1EJVj7QGaDFiOAFfIYFdY32rVMyvfpKC5uyVbjdZ6DeHhKdY7r69evPKcbGwvCIW3stjKNfFm2WDQFK6nhTxFnCafpqLFhJX1Zh019c-3Q"
    },
    {
        "id": 3,
        "name": "SMGwETaAQY",
        "email": "eyJraWQiOiI0ZWYwMWM1OC04YjQ1LTQ4NzQtYmRiMC04NjRlYjVlZjdhZjYiLCJhbGciOiJQUzUxMiJ9.ZXlKcmFXUWlPaUpsWkRJMFpqZ3pOaTAxWlRJeUxUUmxNR010WWpsaU1pMWtZelJsWlRnMU1USTRZVGtpTENKbGJtTWlPaUpCTWpVMlEwSkRMVWhUTlRFeUlpd2lZV3huSWpvaVVsTkJMVTlCUlZBdE1qVTJJbjAuRVR1NlBhclJNYmJob3dfeENKUGp6d0VORmQwRDlzS19lZUx0WHdIX29OT284TnB6ZUdJS2hhUWVVOWoyREtjendFN1pLd0Y4RVJnMGNIUEd2S3hYeTJJLXREWmh4VVQzUjRoQWZPOUxTMDhsVTlNU3ljRmdGNnFwaEZMNnZ2TllDNWdxMmxnQ3dnNmVDY09rRjRjVDZGX3JzOEdyeTM0QnQ2alNhOHNFdlJvMFRTUGxjWDlPS01NUEdMYm5WR2hwQk9fQk5WaEtlYkNyblNpeXhvWmJ0ZWdQcVVYWnczZHdmcEczSjBiY0lVbkZBaU95U0FUcktRbDUwV0oyTkk1Z2dobTN5ZWVGd3NKcW9hbDNfc09TOWg5Z0pJeWlEQUcyMUNsR3N4MklTM0hSOFQ1a1ZySk9XWGtKcm9WbWJxRUJrM3NxWjZ6bVBGZWxoODJ0b3ozNW1pU2hvQUJ5Q1NlY3FvaG9CZnhOazd4OFZZR2RDNkpaa1MwMHYyaFZfcC1henZlYzJ4M0lLMHhRd0N3S3hTTEdfSEdUb3lGdUhhaURFVEZKZlFWcWlJM1hXMmkxbEJCOVpPbXRqOWw4RUg5bTJtS1ZLLWZ0MloweWtLRUQ3dFdrNngxUjN2OGlQMVNwMjNjazB1NG0xeDdUR3FLZ0dDSGZHRmJoWXZ3Wk43N1JsX0R5VmdkOW9aYnhCVTM4SDZhNFBUWGJjdG1USVF4WjE3RTRTZGd1emx3am1qakh1ZDJ1Smlkd0Z5ZnBVZmZKZ0s0VTZKaEtsdDNkY2hZaUZPRGFuN0ZsbDVwZi1MQ0FRMDdZT3lxSU9ObnlQbTF3QmpHckNiNUhoMWtrSERpWVNqVl95c3FrYkI2YVBqc3Utd2h4RTJHMGpqdUxtbV9waURXRzlXVUE4Zk0uR1dxSUZSTE5JeTJjQVNVWXA4S2lxZy40VlN0eGhQY0JwTXVBNEdjUnA0RmpJeUhnNkVWTTZZMHVsQ3ViRXdFWExNLk1YMjNSS0NxV3FtN0VuUExBWm84WlRINmVCTlZYcXp3dTJkNC11ZEQ1cmc.BBEIiqvr5FsYkA9nd1hv7LvGNGjPsxfAsP0wv3WYk2BMoSeSwpeChiw1s2BkZdgLoJo4q12bJWqmx_BYM5DAA96eEbguou6wDnCrbkqGvZI3Vy8gQsqpFfK-hN_V1tR5k8ptC5JeATx901B8l9StVdiYdoDRguuuxz1Pj_R4RqiifAW_R6-KXn5he9SCrzIjuOQUUffiERev8RnbhmsvDdtY4usSbEY64bspM-II-RcTV_Z0bOz78Ai-YZQAvYV-lI6HNcznrll0hGcwFfLfSc_gXDmE4L4eCze_uPs5bcaB7zGsLPBV_Rx-aLFISRoh2OypDXMiwlT2TVG2d4tVsvtmZEk2UpNVDKYaqK3C6KnAXq6Iinzt7OjRT0KHg6D4NwFZyt3u9F-xqyPXnyDCyBB7RxJyT3V5qg1seoun9mWXaqE7I1gJcygbf6lIHSl-KJ0MhDFyIsvnkebqz_EmfXv2x-J4n0BGGMzliO9KAAEjOsUSHK2UR2_KHt0oQUY6xQonqu1GS3clnLUbxUb4bG5pp-04y0iasrd89A_8WRgNyTCsQqhsUls3x2NE6hN1R7wqZ-Hp3vnc0KIXknt_w-6P1shTu1Ypz6E8BJnnrtAGzl0NhzrbGNfzW17nm02-Qk5FJ7Jd5fup495dIppUuMjBCpD_iqPeuRGgqeeRU1M"
    }
]
```


##### Check the actuator endpoints

Let's check our JOSE database actuator endpoints.

```
curl --location --request GET 'http://localhost:8080/actuator/jose-database'
```

```
{
  "validKeys": {
    "keys": [
      {
        "kty": "RSA",
        "e": "AQAB",
        "use": "sig",
        "kid": "4ef01c58-8b45-4874-bdb0-864eb5ef7af6",
        "n": "mpWKCJHMEKF2sijLHINvvTbKbmk_APGtOYQRZiAaf_detrNStT6NR_4eaEkkCZrfDteOZZ6OvKWLp_hIkDyz3PV6bq1CdRhQL0yT_RVjJ-jRyMZYwgcldULJORnBZ6HIOFGnPoPT-FyAuE0IDW-ttoGrpAF_IAsS1hMGCTI5xo97HbfMlKu93w8FfWY3-ke8JsjRF7jc5nNYmjzrM90OGiKBXfM84aajY3SEzWhuxzXTSTTT4PqkgaBI7vDu9O-qifDhxE9ZNIPBpVbj3YNw9443LuA1Ylp5xaGQdWyFOZV0CFtGX4FphlZsA5te3gPd0Dhn9vR1SVLk06T6GD5eUII5LfYP0ROcbCH4bpq8mvgJZFcsBhH6ua9k0EvEymSgpEH5AeHCnizWk-2rh_rk441i1wEPxikdqQQWj-f6xgmcFowmF-IGg6OGZ7iECZYZHtUdM9MzuVMZ49VKjJ7mbGd9WSEdSKzEr4UhMACGy9pbj4Nk6GAwMwb3N1WhpX7D6bTub5uspoKx-BaDde8IViOahLsIiKzCIATtMHAhR4l17R_UKEQ7ebpxwfGhDrrLK2Ih1fYpr4mk4MWwYoqvA7FEtaqBHigOwNwAutLCxcn8C6dvBbyPjg8agfKz3Ex5jzZfl1OvxC41DBY4SEGArGOZpnaFMrs7OcfSeEAMYz0"
      },
      {
        "kty": "RSA",
        "e": "AQAB",
        "use": "enc",
        "kid": "ed24f836-5e22-4e0c-b9b2-dc4ee85128a9",
        "n": "oxeuO_A0pBC-yUCOAVYN10H9HOz34-jksG_MG4rPRSwi7esUG1NbZ9V7D286gfGAh_Z4rp2mh0FqObscpstMoOsy3vEwas4CAkbhLk8OEmiWrLnR_VuTEI8Dog0X8GoyhXFLzWa61UqFQuotizEyA03aXT4qC0CJRm4I3YuIzReZU20npVWNuTyAtuHtsgLafYzsC5L92kV_-Dmjhe17yYb-X_FppyWCxIlgxz3uPo-N4cxBxChr2jyKxhPPkGTEEI23YEpRdMINEyNijVrwSWOr44AfosTDVRQiApPqyNYzoznMaSl-HznYVdLnipWi07uaNp4w3EeRJuLxHwfdG3D6T22oGrHLv5Sgy3H-9UdyS0OLoElQfuN0Z1x_qX9wSAyKYyEDfTm5VfvosRJj-lQVJloZuAX03r3VbsKbdILT6eBQvBhOjZf_-JSRJb8kSlDYKX7Eyg_KfIbLiNK03259xUiMjcWqwGcsvtMQM9sKob7_LDWNalcYDFiLYmfVqmrHs7ZEWFdx9lr_AspRo27X8sMRgNYiaslhjfVgbB61XbHhRFo7czeYbLFlZSnMh6h60wunfGpqeWjLub4HEJG2BQhhQF6Oo2eTv_ULrZDawRTIUuK_u333KmgBJZd87vJLaSyjnds7Wx5oxFHX3r4hwL7RFbedMcZO86_OZQ0"
      }
    ]
  },
  "expiredKeys": {
    "keys": []
  },
  "revokedKeys": {
    "keys": []
  },
  "currentEncryptionKey": {
    "kty": "RSA",
    "e": "AQAB",
    "use": "enc",
    "kid": "ed24f836-5e22-4e0c-b9b2-dc4ee85128a9",
    "n": "oxeuO_A0pBC-yUCOAVYN10H9HOz34-jksG_MG4rPRSwi7esUG1NbZ9V7D286gfGAh_Z4rp2mh0FqObscpstMoOsy3vEwas4CAkbhLk8OEmiWrLnR_VuTEI8Dog0X8GoyhXFLzWa61UqFQuotizEyA03aXT4qC0CJRm4I3YuIzReZU20npVWNuTyAtuHtsgLafYzsC5L92kV_-Dmjhe17yYb-X_FppyWCxIlgxz3uPo-N4cxBxChr2jyKxhPPkGTEEI23YEpRdMINEyNijVrwSWOr44AfosTDVRQiApPqyNYzoznMaSl-HznYVdLnipWi07uaNp4w3EeRJuLxHwfdG3D6T22oGrHLv5Sgy3H-9UdyS0OLoElQfuN0Z1x_qX9wSAyKYyEDfTm5VfvosRJj-lQVJloZuAX03r3VbsKbdILT6eBQvBhOjZf_-JSRJb8kSlDYKX7Eyg_KfIbLiNK03259xUiMjcWqwGcsvtMQM9sKob7_LDWNalcYDFiLYmfVqmrHs7ZEWFdx9lr_AspRo27X8sMRgNYiaslhjfVgbB61XbHhRFo7czeYbLFlZSnMh6h60wunfGpqeWjLub4HEJG2BQhhQF6Oo2eTv_ULrZDawRTIUuK_u333KmgBJZd87vJLaSyjnds7Wx5oxFHX3r4hwL7RFbedMcZO86_OZQ0"
  },
  "currentSigningKey": {
    "kty": "RSA",
    "e": "AQAB",
    "use": "sig",
    "kid": "4ef01c58-8b45-4874-bdb0-864eb5ef7af6",
    "n": "mpWKCJHMEKF2sijLHINvvTbKbmk_APGtOYQRZiAaf_detrNStT6NR_4eaEkkCZrfDteOZZ6OvKWLp_hIkDyz3PV6bq1CdRhQL0yT_RVjJ-jRyMZYwgcldULJORnBZ6HIOFGnPoPT-FyAuE0IDW-ttoGrpAF_IAsS1hMGCTI5xo97HbfMlKu93w8FfWY3-ke8JsjRF7jc5nNYmjzrM90OGiKBXfM84aajY3SEzWhuxzXTSTTT4PqkgaBI7vDu9O-qifDhxE9ZNIPBpVbj3YNw9443LuA1Ylp5xaGQdWyFOZV0CFtGX4FphlZsA5te3gPd0Dhn9vR1SVLk06T6GD5eUII5LfYP0ROcbCH4bpq8mvgJZFcsBhH6ua9k0EvEymSgpEH5AeHCnizWk-2rh_rk441i1wEPxikdqQQWj-f6xgmcFowmF-IGg6OGZ7iECZYZHtUdM9MzuVMZ49VKjJ7mbGd9WSEdSKzEr4UhMACGy9pbj4Nk6GAwMwb3N1WhpX7D6bTub5uspoKx-BaDde8IViOahLsIiKzCIATtMHAhR4l17R_UKEQ7ebpxwfGhDrrLK2Ih1fYpr4mk4MWwYoqvA7FEtaqBHigOwNwAutLCxcn8C6dvBbyPjg8agfKz3Ex5jzZfl1OvxC41DBY4SEGArGOZpnaFMrs7OcfSeEAMYz0"
  },
  "encryptionMethod": "A256CBC-HS512"
}
```

As you can see, your JWK sets have been loaded properly by our application which says it has only 2 valid keys and no revoked or expired keys.
We are all set. Our system is in cruise status, meaning that it could start serving the APIs and keep encrypting/decrypting emails.

### Step 2: Key rotation

Now let's see how we can rotate our keys and make sure they are propagated properly to our applications and the old fields of the database.

#### Generate a new set of keys by rotating the current keys

We will use our CLI for that:

```
./jose-cli/jose jwks-sets rotate -k ./keys -o ./keys
```

```
   "2020-07-10 14:48:29.163  INFO : Starting JoseCLIRunner on quentins-MacBook-Pro-2.local with PID 26267 (/Users/quentincastel/Development/GIT/github/yapily/jose-database-field-demo/jose-cli/jose-cli.jar started by quentincastel in /Users/quentincastel/Development/GIT/github/yapily/jose-database-field-demo)
   "2020-07-10 14:48:29.168 DEBUG : Running with Spring Boot v2.2.2.RELEASE, Spring v5.2.2.RELEASE
   "2020-07-10 14:48:29.168  INFO : The following profiles are active: cli
   "2020-07-10 14:48:30.093  INFO : Started JoseCLIRunner in 1.554 seconds (JVM running for 2.469)
   "2020-07-10 14:48:30.246  INFO : Rotate the keys
   "2020-07-10 14:48:30.247  INFO : Move all the valid keys as expired.
   "2020-07-10 14:48:30.247  INFO : Create new keys
   "2020-07-10 14:48:30.247  INFO : Create a new RSA key for 'sig'
   "2020-07-10 14:48:33.020  INFO : Create a new RSA key for 'enc'
   "2020-07-10 14:48:34.006  INFO : Save rotated JWK sets into './keys'
   "2020-07-10 14:48:34.030  INFO : Key rotation completed.
```

the `-k` is to specify our current set of keys and `-o` where we want to output the new one. Here, we are overriding the current one with the new set of keys.

You will have a different set of keys than me. In my case, I got the following keys:

```
cat ./keys/valid-keys.json
```

```
{
  "keys": [
    {
      "p": "4N4Tloon5aukQ-maDJgTwwIoEZyXXMDLwfGf9_QJs4j3_yFUucMkU777wAjfdXFW5xPgiYcfgxcNn6UiaNHavSPNZmYoWgYAtlRn_dmga_Edp6-EAK-DHdCNWjdpo03kDdYp6kD2HcFFDysI0qvepmwwa-TlzrZ4z8dMYC9Hhu7MGO0VmqzPmwz1u31oNK6S-GcaVw0slDr605Z8_gmdqO3fXIBDqhCX1UFxXIz5UX4O1OOKGvis4QxlfPGPkDrZu41YkLf0HSrG6Kf3oF8r0_b2opGtuDefFyoQBAGe2RIIBTHHA9qpFyJRyr2jkHF2IleAR_92mRtiTGh8T1yIVQ",
      "kty": "RSA",
      "q": "pJjan6F3bi0LK__-qZLR9SrzLGauAKiHCgVKh7ekyosdslNeKCYLXocXp2Nhnv7TGD1Cdp5pyB9QomqLFlWewFlTVv5kFyeUXhTDmdlgFN6zgr4MoYFV2in0p9Kn0z3uvenK7AlX-EF01h2KjwSnXrRnRVmE-JDTxsIF0iRm9_JtAKn2lkgSD9kL7mhiADBVzBPzncprvh4S-jkU6cdC8ocLAhr95upjSBsitt3HXPYrGeCSAuh9JzJspbv5V05n3etiArJ6gmZiOXheFM0tbkGiqJhVkqhLvcu5uzo4bMPZTZjAvwR5tmuLWr6t6WkMMfAJvl7t0o_82_Hri8Pd8w",
      "d": "SvOllFw_XucKKlGM9Fd-O0zkLY3YT4W_GBdYxL03tCsAUqWuUK6sxJXH2aJDeJSCNFIydsb04l9ExolKiOhxAp1UUVQg0ZZuw2oKLAGd2A_qSCJl99nb9Smz3fhQ3TyIxuKG8AB9jB0YQXjk-Zfy2R4N1jbDiE-_bcLc_zKQjrEO1TWpyC3K6Y5aY-0QjCkd93fCD6QCGQtb_R52UWew6SxqAUGtCzkxiWSC_2QzGkJoOnHD1cEOmYdk7DPChlZi_rceEsZiHy_B6RB0jGUUJO9jbTaeFNV8uFW4j7CnMq-Lqrpk5qLDab9nfhOrTTQJyr-fYj9OKONg_gyvGknruHE_pY9CuSat-ciTJQ2W-fnvcF7sNIyY1ts44DkZnDUCPMbOfdmdgFKmWQsxtdhk8mmcK697hD4lQIaO5tUf_jYYdUeAd6UeOGpI-oLJ-isgSP-Fy5Uo0OJrdwOhe25V6o-iFuAW0KWlyNntmx3LnI-P_m-uFf_QwhYHZO9agrvLkQDYLzs3O2P04yMrYCP5gOoUAY18GFax89Gix7WkIJZblIYcx43N3XTXb4SnjQuoYtXMsnZ1PiM2-PGlJV_jxDGlP2a_ow56ycWQc8rsOIunt6rORvctI5OnoEMAsZozJsk9wnPDlV62F32geUAcgmVM5XC10d7DCofwIr-ir1k",
      "e": "AQAB",
      "use": "sig",
      "kid": "8e9bb522-bde6-4491-ae0b-3e92427c0ad2",
      "qi": "WGAJTFXn9pa7u0jH7Q4l32RnqoC1QWXamideo8khARhvZfWeXX7bJ5ChjypqtttIZqkFe-wp7fUSubp1H30nA_D9TKc-2mU--J9GrUbp3xWqit8bEq-GAlH-mknh8J9_aRuXrnjRt-m5e10Wqupq-4Ga3bo9XHuxXZY4S6fsso694l_XAH3aPBlCXWiJAo2Stp2vRGyBs3Wd2p24n2E-i-XgaqT1xslmpa_DvDEQ0d4TE6uJWGQyYJXli_NFt0Xfh7TDdqjfn1s0i31rTKudb4wkoK28Qxxyh8t4-5rOfdw3JuYFpMAiF9B4izxnl_k7JlBlf7GsDioAbuZkxloimg",
      "dp": "CZVNTmga5S3YdVB6UTkV9oScAowi40AQLvbGM4IB-9XFg-j-vF_1p2AHSJrYMyAebQCq3BGXJTYRTZFzEvaGlPL2qPuHkraYxyx3thjVPmRrOB9Bx-my881UiNi9tsj2BCTxaltUYdY_xDK4UIoklgEcWyzJInMiWPCMb7j0GmsI5bMM7aeZvWji_BePHlemSdHTDIyyLqsIz4WlVApC3tUsZLvOpmvInL0KQMB9S5zMswPzeczJSVoG5TlCPgpUBysx8BD25VtSWM33vqzvqDvpLOp6ddRhAlK3lgQfqe6byuThL2fdNvAufi5wzxIWz8Xc4LShcEr7eHgnfBZcdQ",
      "dq": "MdNvwwj-_WQ3UI-DCNRAKKZizJscq2hY0Ki_Ygwun32zdKsWArNZl7jvaSWFhLsBLgJFX2EfBoysPS5hglZS40lnGBuUMwoxOLWybJ1IH6k20Co16qIbWhp_TFpRoXnLDsR6QC-n6Kfv9W0l55tyCxIzfOPXg-NbqHxNhMquPPqvEpdg5SdWCGQc34PLvuDi6A9WHPsM2JWECylCcm52jrJgm7eOCtwDR-2m-ZJzamm-rGu155l3YWk7SIR5u3spqB08IzLiMR1LOLh-Sm--A_VshvruVKILo53LUKkUMaCr5HiMSIbTTWyK1-KHBPRe07MlT_KuraIW2oIGWt3lDw",
      "n": "kJSIcWZUhUvgontICnY-J33aNUHY0EBeQtLCU7pwzwVFR_8aYfJfZuiymqXxay8-FjN0Xp6bsntHzJEhmY_o0Lxdol_BWi4hsfWCGzrPJgMTrJQAlvBZNNrpxubJa8SUWADEaPj_I1YTFTB9pDdXC1Ty3lxoIEY5SsWDCnKa0pV9Z_5dZu6I4AIzJjxbyPJ17SkZgFQK-pQKGbZwhqZQWBvQFx1lZ5Wz3wrgOXC_zFJUn7nSUcqFAup9uaTNaylS_aCZL0ziyKPJuXIJ4I-17nhdtY4UzrIQCHUhSuJL3zqC9aPQiZ2nd9l9nHyy4ucPLH1OqAjdOkeFV3xjXPtCtzJDqUf6RYOQ9L9vkp0i_WqL9W1SUO_k0TIugTSCGyay71sN-OAc2HFZoG6nkdUsUWESgDpZHlcTfIZHRBkVnJqOXuU0nrBKMGTbYEKYeoZkHQa0Bm1X-hQ6zo5nduPCqXp1yy9pTUSl0QDP516D6WhIr57z8HCxSuf4YtEZZkC3-5SGMUgCbSfxbiE1vPCrUQxPj1ub1X8R65mF0_XI1O6F9WvHpFqcgIqaQTUF_jak3E_yxpifJ6twLvpvMsHyAf4W0etJ5Q0QVHgvR3_mttOnuglzjWGlZiW4pCzIufnhnKr6DvjrjAnOnunDaNyBwjtRf5fyKiJznYJbCzZFya8"
    },
    {
      "p": "7hQYLHKUl6R5ycZBD-w_akzTy2lAewVUSYKDi9rMNXEg1414JjHqGJ5GB5yItb99WToou8y41--VoT47QYxNPEliyc9cdD9Bx7TYDxkoWoy5kPLjp-xrx_ZLzeaus6gDufE21CfCLBRbs83gTKTBFItkDiSIm_VFjqSJkCzH9rqtgYD3EAQK5laGm4_A6TmCn2J061-vWmtboPsv0l9Ln7mmjVmSYQ_netUQLCQnKsJNrO92-4t9sL3WaQRgJRogXLAPRgn3cagA7gSIj2nXFzTzavimf43nzYrmq62eKHP0IF9sw4YghybMDmyPotelKxYiBRs-tS-NbdLz1yxUPw",
      "kty": "RSA",
      "q": "jHC6m_KYUY0XgedhUu8pCmVV1H4ci0EQiLyj-XkWG8k_IbWG5yAxshBRHlpTh4zFikmbCVKrrhy8am6crJHIVeKLHUwRAdQjXeAmoT1TQ8XMpV9G-8N4O_FRRsDt61OOTug4qTLceYPYAAbASqre2ZkIe5Csu8328uZPEUiphr0nelktaFiQ52j6YHOKBftqQbrw5bi1CL9r97FJfBV6UuzHvy6XaX6WDst9YIoG8Fe64hmUvKX9jefR8DBjI0jJDW-oVVP9DJqfpFDcc3yjWCnpvDrVD8exM_AEO9jsri6u0CgTY3xy-KiXwmDKq2cakZvcXi1-n61pQmiwapGhCw",
      "d": "JzNJ3d5KJajczmorVHIlR8gkoSc8h0PHbmZ-IMhdA5yqpVr6MYry6TF8QiUSl4J502B9p6lT_cOyxEPTRNq0cvJTiZ2VBb6yJs5cJF2THbGozOhfizokqMB85C6puPGs_94Vn7AmPPsfRw6qK12VY4ljACjikrnWpf0Pihv6MnbaHHsY_LArD-c3BcQ-89VHGRHvj3WZLdKMAoWyyvDm2PJmASgoJ5UiJt5CFhJp-_WmmjeYIGEPcee7CNAc-fVzGP_R9hJzABlWA3oxr_dxTSoObzJIddsWSHZVKyCG8ZkIqLAgnfPo83CEu7YsWWbqeu-QrnL7C7_y9nuAjkAbUtAcGzZjm9WPGWIn0YhOpmO3tGke58rRRo-GbbzIC_Ffd4vlOI8DKyP8GsZI5PgVSP3zhU9CkKBZA3iI6j1ucNvQ1iTXWEJuLfvGS07mHE1693FY8OSCGWacHfH5vozz0mp8xBV-sWLOTTn8pimbxt3KkSp8KhqYiePfd1jzZKScbstIJCZpKuSaSAWiTX9wxC9XzrELMqDtVvOH7eHcIva2XapS0sf6mOwCH4i1PYL3DQUtPuYkqjfS1kKAAGc-JjjXGmUuMDN6grqjw75wwczATcrSWPoo3oFjw9W95Os74NJFjJf_s9FAU7m-P_aNEb96DqPOMZjkUSCtiw6rmkk",
      "e": "AQAB",
      "use": "enc",
      "kid": "494ea5dc-f8c7-4439-971f-5349a898d05c",
      "qi": "dI54zVIXso-XufohettXjOI5Jlbrhga2sbeoPO7bea6dZmoeiVqy-wgYI2tKkw0s9ZjSfSrEyqQ8ko7zSduJMLd9-JxgBPsyCwZ7uAF2yNc-z3_iuD5-c2gWY7gXwdifu58tE_QTAgFuWzT7aG8QKV0t53abSxT9zSBlPt2HglT5Zu99x_NSuXcn3V4dYSEmd40HsAP4Yi3F7w9UZ8Eu6gIQ8oYGoE9XMXgsuCTy6aDk9XBM6AN2GW2Y_3wqQhYvuyLGeeHG3FXQvszkBUzkcV_K6s2qiQP44Ic6MRg5q6mbfouaOAdLwsBodscpMD1csG4N2RiSXl8X6HrBKeqjKQ",
      "dp": "VBrlL06LD8ca_xz6fxWGkZbyezmDffI0BIQG1LFG0tpUL9HaUCPx_yBqvOWfphUvwwW4vh9kbWRGB-BkLpPR9So3q_OFRcvTASnx6eOJTfPI2lvz8K6kpM9tmB-WOAFyz41XQTuKbOgEPVDNnEXXr9pOTnC5kd7j47BcqIIpYhAwmz9kCFRlRVnr06jFDZ8zdfriMwRqfhbPF9-k1Ty1CogbPnQWMhPcQphiTjW3YHOj3SP7dIGitX1a8V6KWJESPaw0uRlsQMqJCYywcXmCcsBR_cg5iXYoHYSkWKHM7Z2I8KbFVY1ckGI0VCQl2fE0eDQpcIRfcsKJw_JyEeGnIQ",
      "dq": "IO_HNA-_HVEjtDmZn6OrUl7VtFInS5lk0Uy8gCOfxcB65-L00nvAa-xbueyhLmcbZQVCMPuMe_cNUUDDyc0e2NWanrJaQr2H1dpd9O62-mwHRLmFokIDjrtXvmo84jXkaCkaMMRW5MDBBbCPpHNSdGHplWEmwZzaT7knzfvmlk0CVzVW6uPqh_sczUYLlr-R-fxnxth8zKJoJd0USN-Yop4ZcoLwy7L-alXa_6sfWXqZv7EUKvIa9w7pM5WFip7lWBtZtTlPXYdd7WFxcjxG6sRZpuV9VogUzQN7WrXTPD6CjRr5ARoMtLiFbvbYUT6LAp81HXk5-yTmTMbUqQmiZQ",
      "n": "gpvTjoNy-LmR_W-PHj1Lvq88YRaW5kLGUiyRwXUhSpiqkk6NFip8bDxa8TTgHxfVmJbGtgl4X-N6hTNZoNyklHTM9JZuxdIH1BGiYaIG3L15nfz8iGqdhB1UoW3X23-zLli6V5YVxn4XFypdrjZOrPndMVMLKV3ABGvVglLHGvREqDfg3a6g6kuGCeG2BnVTtyGCFmaDLlrd46ZscnlExJdFDOmoYaCZNabwhEnSAsEevTX9WDq4YSGdxZQoN2wdPKkgRnDuC_paYozjAVebEMfZY3fPK6VDVRkxFacO4_dIkQvplvOsfSs8utNO48ztubHoFD8dbD5ZkrVPEwlCzzVRgquksLahZwpBek1pQHQL402-7OlyvW-iQ9tM3ZKHQH7oNhuA1V72T8El1XVeZEz3ghuXSHMeuJHjYqVSaEUez1cG-5RNPO4_Nz2KTBVR1V834UFO-1IkokmsOdJ5T4iDal2BBlDWpanOvyCC_LGXH1J27W7pRZHUFUJApnXNq2KM9SfepJKZK6ieXXbMOtbOm17ZtXiJcoRvKrHWQ65rCVbsjB1z0mbQNPtk1PUhyj9CJ8wgToGie6xZAfOxkOAHV_pvLbOhRe0G17MtsrRS8w7-2THO9K9cUqbJZQhCBAgQlUhOPdKV9J5bUFIcZNlnPRCmwBQnGIopGu2SPbU"
    }
  ]
}
```

```
cat ./keys/expired-keys.json
```

```
{
  "keys": [
    {
      "p": "yFn8N-jGb2QNi4QmkrSbwwpuoIDQrs6D3BRffy-aS6Tl0r2HZTw3J_NeeNMA1dK3CTwI5zW_XzXngVyBJnEjGaAHlYIDAJdpTkSCH5Av2m1hd7s4IxzB-oS1g4redvNPaMPcRIR8Ba5LoEb24yAFxOdWZP8UghjxlNHCjDPcXemwCPRTbHL76a1WkZmL_ufG4lKHsRY2JGiI4UimnZzxJWxZ-4EfBaJ3qB8XQIMwNHOdLuY_fIovC7Gr3qFqxSgM5Dt0xb1p8Rd8lVJ3se1jTkwYipKeG_T-R0ModVap43ht2kpuW7gpMiOB7-TN-p02RzgWHwVj8vmjgB8SoJTBmw",
      "kty": "RSA",
      "q": "xYVCsLo19zpz5-vUZujjX28uuHIlukjdg_BsgB_DhheIkX0etKR42xZadOMNHe9IcnMqGjXvFw8TaSeWrlLl8VlUppaNw8e6DjCgO9pQu9QKleVVB3u7cdJXrgi4xPYNT3rfjAiFL2MTOMryGL7Sj6UTObYtCgYNqq-5stRYgO_lC6ZUERSzaRS0u91tjOXwRdUzLr6RTsfXBtg7bXyjB4K3VBCE3lB_gvOOb9RhFdz1s_DJojFj_0gptT5HSFZR3lRSml3cpEnAoTUaXKwK7vlWK1mnNGqGfx-gX1eUQlIw8mXylTUKAN1YMpEA1mrUSiKe4olYHde2sG1MLs7IBw",
      "d": "d48xM2-QZUx8gU8LyQHfanj2GG9OS2ygjduXmWU7Gb45gxUu4v9-RjBFVGM9fDBMVS5i9DWuMDnZyhA-D1DjzAIQr4lR3T7wxaR00Um5ZySrHJ5MB8V_MzJ0b7V74KtAME73C2Lqjyz8YY8yqzG1KXMhTbfamu31o50TDgnKpchTcKi3kkVClUMI-_QTV4AkBdtL3Q0dzR9pZ-G2CGbwgUYuyHtYmy5HMKTuEfCzvV_kCrrRJTC2C4vs3OF77_Vt8UJdW3gDhpphK4AIvUrsFIvqIldG8ssHY9OR50AHyoHNPKlbxIJTG5O79w40UYKsDF-UCW0JSazTTt5KkHWse2SCKmI3ykYCHYDgCI6VgPGsQjxd5b66F5NMsAZyy4bQ-tiGYnY2R_4Mc1WEhtz-H2eRiUYvpCSvcfhVUImLzKElOvSvgdFtowlUUlGvbkZyjdO8lSnS5oGUMG5vE1t8XqUryICHFr998Oci6cqwG6lijexR7rYWl_WtGQZhproIg2t9YUnvTT_GUV0uSwZ0UIIqRfVEEi50jCG54uhBDHiD1X898ySE8HXzGedk76APMeNB9yyn_WH66K2euuIYvXfieGsSWakYirlw_Ba74vryQnCIsEr83DFh58tQjQFeTJNw8J9x3LBtrjTaNRLIOmBiTl4oQZ-MQ2K81PBNqAE",
      "e": "AQAB",
      "use": "sig",
      "kid": "4ef01c58-8b45-4874-bdb0-864eb5ef7af6",
      "qi": "uYWwUt0X6u3WPLOCq2ABg7wfs5gJnJt2Ng36La77FX123mxH3nAPeRUa1EIJuBvuHKS_j9QdnJtSmgXkeDihEu7UkOyfE_RFGXYB2-8Za0Tn2idfURHAyVXHcn2FNsFH4yDhIR750zLTcYrNnOfv_YRkLTHaKbowIWj_Ggo77QI9pv6jDUIhh6U-fGSwx4oTFoRmE3zVBBsZkRPY4L66IW5ecbzGS7OMSwkGiGtJgeoGBw4Icst0aL7Pdsv0Z9oeki4WlgV_ZYUInwVLKxRqVPQUuHJw_Hx3ksZcYdWS6MijMWULSQr6uu4pUASZb58nuKUIFmxVdEc_w6V4HmpY6A",
      "dp": "ESpciLHB5qNM4co1v9YHjmNjbQ78Ui3RcFayg-QhNR_wrOieJBdPpnWcf3-cj7VZB0qEfMLHHbIxnoy9Z_zDhBuI6s9T0IYoeJM4Dto8r1s1wZthyOha2krqflW0j75y7EfzpE8xjQ8M3bOWzYjd1av4Y_jS2f6nHBgIshhzZfxyn6HHUABeAvxd8WWVcAaNrLBjfdGoW-A08hBWbiSXKJyq7Ph4HvR3igj35ggXZjcNnYoOoG36b-cOEaoEytWOnTNW4uZCoGxhuS1yoeTz4C5239CGCWEpNBPV8I-3SIz15IECCYuyw9_07wfMeg9kKh_tDUVZ9eRblMuUvftgxw",
      "dq": "bBuZ-tyQePZ3RfLf2EuTV0KlUbK4T5NeGr7Ww_GetJ_g0bCnw7tZoqBDdkJnhYDYKDw8SQhICljoCN_wKw2YaVa5bExu3z9L6ERZDylYktVlzdEtjuD8ITo9XzXK25LIij34aM6pBty1Qpphk4Wt_Y0eTrIVSK0zgb6YZvauUjP7KzsoPAkWgWch3Mk_EqUvsykT0HvhUDs0x7xGGpDjMlMrQEmiT4PDpsrjpPWbBbpgiNI7qhhaYPkASwEBRuhn-sYwR-kjwI_VbOaBvUM04MN7lFKHn7rGToHi5wwqS3ps6988vMnA5eRAR7zxtmnWM5VrXRZLMlpE1Z-c2Om4tQ",
      "n": "mpWKCJHMEKF2sijLHINvvTbKbmk_APGtOYQRZiAaf_detrNStT6NR_4eaEkkCZrfDteOZZ6OvKWLp_hIkDyz3PV6bq1CdRhQL0yT_RVjJ-jRyMZYwgcldULJORnBZ6HIOFGnPoPT-FyAuE0IDW-ttoGrpAF_IAsS1hMGCTI5xo97HbfMlKu93w8FfWY3-ke8JsjRF7jc5nNYmjzrM90OGiKBXfM84aajY3SEzWhuxzXTSTTT4PqkgaBI7vDu9O-qifDhxE9ZNIPBpVbj3YNw9443LuA1Ylp5xaGQdWyFOZV0CFtGX4FphlZsA5te3gPd0Dhn9vR1SVLk06T6GD5eUII5LfYP0ROcbCH4bpq8mvgJZFcsBhH6ua9k0EvEymSgpEH5AeHCnizWk-2rh_rk441i1wEPxikdqQQWj-f6xgmcFowmF-IGg6OGZ7iECZYZHtUdM9MzuVMZ49VKjJ7mbGd9WSEdSKzEr4UhMACGy9pbj4Nk6GAwMwb3N1WhpX7D6bTub5uspoKx-BaDde8IViOahLsIiKzCIATtMHAhR4l17R_UKEQ7ebpxwfGhDrrLK2Ih1fYpr4mk4MWwYoqvA7FEtaqBHigOwNwAutLCxcn8C6dvBbyPjg8agfKz3Ex5jzZfl1OvxC41DBY4SEGArGOZpnaFMrs7OcfSeEAMYz0"
    },
    {
      "p": "2m6rh140AtbRLk7InkZrb74-NoAjZgpZ4WHsmjk2I-wTpFCl16nvaxhzT-JMKiQ1rwLUHO771D_I4memf24z47cdxNMwqaoyZznkqut1H9CBachFmB3ric6UckoKrVWrjjecMn95qwvMhqcRdQhxcUCy-J_SyJHIsfy8DLVCuDasisnZf2P9fxtQxDoadhoXMlryIaJyuo6dK_pK4qq2ZWDnlFJrKkn9evqa0zFU7yawZCKegysZIrJrOh6NjVvu6K-vbqxhZRjsJMABgwhMK12ozAMPCZHAGGAp6Y2SxawZ-axyDXwLgB2ahrCsGvdfeJfNXxYIM6gMCLE7MnFHeQ",
      "kty": "RSA",
      "q": "vyR2A0pzFE63VXoXRCyh7dbhf3BCKcC8qmOx-iP0ufD25DJpmUq5HEjYJB20ybFnwhywnx6J5gw1dpNQMSXAnJ1OK1p4tMEaRmFyV7ij6IQ49E-PQiNJN0_EEZbS9scJxgJY-YDvSBbpUJgHI-oyuYFpn_OSwOG0ggf5DAGt6i8sqUa3WaaaAWOHySkvCjf6l4qyuwEKYf3F52TR7Khn-TSUClEtKL17thdtoJJVctoRyL3J7LSGuXkIeyaYN9cs6g24dhO_KtlckxMLTBXMHq1IHDVNnOXnhRkmgND7SVpnBRkfCV8Otqmxs7wn6CK4Yv9Pdj59F6QlKTG96EQhNQ",
      "d": "JuavGHzjEX8tArPzKz5rbry-sveS6TnT0oHYHXmulsFAU5-O3DW1x76T68qMGhKQpd2US5Iu9nQT8lE_NEmprXhsWeENu2wH7qMZcwJISPvJdMmd3ODK41g5dZ3J5_5sroennQaz6cwsnRleY6UIWOFpBHSfcq-iDrqQQsfViZXnbI7MthMCCvzzdqvNFni3xNqoMtqM0lNhgsO8zWUn8ZnRcvESUlwQJPDftKaA_UsgfItL5I3_q75lG3n-OGLQoWIsKzuO0lq246m9CBp41giAJ3OYGCr2rIRw2EyP4_ezuWs_5pWPD8OsZkw4DkZC4F4PGpgA74nihXP8L3qgslk-syNB60EoBFpwJi_HHzoZFxpfvFIkEA9aEWorUCkaBcJIPR3xa6pedaSVS-z96An6rOIV5qlFTsC_m4QxZccywcdVllHgcst11VQJRs8UEzHDzmyvNh3d0XkgoFQs114MbsdgZigRavOJq-yQwIU7xwazDmBejsGfC2CeO8TsNurmI9rAu86JjsJMMpYD76l4YjxkYV8S-iPf0T1STwlUkYglXbMaIrkLtT0-Jl8hq6kf2-3sbh8_yQxZPVJMvEQQ6OpmtD-D692gCRsy4DhTuUPn0sPuLU-OtUBaV-qk1PURJ8mKWlwPkr2uilM90QKYLyCyJYCwkYLVfp-xpgE",
      "e": "AQAB",
      "use": "enc",
      "kid": "ed24f836-5e22-4e0c-b9b2-dc4ee85128a9",
      "qi": "cg1hbE1o23AyOUyeJwZWE6WwJJTpFdK9onK9NYHbQLkwXO9fX-rhQl7FEegc81LpRrGW41Vc3f8aNoZfjBTD-xWsIyGoutyzCwj6TuQima3iRqjj6VwDM4oPElYUlQX6jNb4RDpJOYmBZHq7jj6gYwaMQm2RPRh_-01FynIbvPayBOYgnfLjudrtwcNU4-Q-6IGXj-V0lSoAzCCZBHWg6YyrzCEtiZGa5pQPVwK3JeYx_hTEDU1m3Eq15sSqSwmdQIVbHxKl6YGqX-BbFEC8RNz9oWJA1Cadsf4QdE4tvn00dLXdZiogn5Q705e2qnu48Uww78OO2EBYt3c_Ygypsg",
      "dp": "HMlrB6sNlbYz2TN-0wUa0Z4z2sDmaWNB1yctpGGX1gId0JDkWljF-co-IDAFs6QUAx2PUMTaIW3KjrP0SGUAp6kRXkgq6KFKhTom_bOMOwYimAtRyKtgyEeWXr2NTKy2sEZ56lnMchNa___ymAbl1HQfYg7GG7LCzVouekpFIbvq31ucs2I9HUw_R60Uoa3skrFTqcUb86Qp6IrS5a5z3UZ5Hp4CC4-2vUdbsiuVMvNZWckKNOUwKddztDQkmZWdFcNZjm9fYpB3RpybVmZ4i1qLjV910uAHfe7mdyY2SqDUx4fHfCl15ouAOvH7rI4fYeg3o6lmqVAjTd31nD87mQ",
      "dq": "FrkP6n48RgrBksDL6UfhDDRCZHME-o-2Hg9yBgmmO0ChKSmxEg8nCGzEfS6m4l9btWDRwmjP_fAvnuQOYXlad4Pf2hmMRfi61ekZvcHugmLNnoWiwzsMpi2uYmTquXmZ-NcomqiwSYKnw_P-zU83LoRq-R_sr09ltRubiMjeUvu-tHT6sQa0QjwB1XE27WEQQlmzu7V49YNEtqhuqFKw4ZhVjRBvbOtxvIj2eGNNEzVHbfTZ_3DKn1v7HSDOcbz70utEpYzdagujDLzz19yzgmYZL3lKu789Eb6WrDOr2-GKY7nDzcpmJmLwBCz3FYqjHum5GZZ-KoRSAp6uM-F9jQ",
      "n": "oxeuO_A0pBC-yUCOAVYN10H9HOz34-jksG_MG4rPRSwi7esUG1NbZ9V7D286gfGAh_Z4rp2mh0FqObscpstMoOsy3vEwas4CAkbhLk8OEmiWrLnR_VuTEI8Dog0X8GoyhXFLzWa61UqFQuotizEyA03aXT4qC0CJRm4I3YuIzReZU20npVWNuTyAtuHtsgLafYzsC5L92kV_-Dmjhe17yYb-X_FppyWCxIlgxz3uPo-N4cxBxChr2jyKxhPPkGTEEI23YEpRdMINEyNijVrwSWOr44AfosTDVRQiApPqyNYzoznMaSl-HznYVdLnipWi07uaNp4w3EeRJuLxHwfdG3D6T22oGrHLv5Sgy3H-9UdyS0OLoElQfuN0Z1x_qX9wSAyKYyEDfTm5VfvosRJj-lQVJloZuAX03r3VbsKbdILT6eBQvBhOjZf_-JSRJb8kSlDYKX7Eyg_KfIbLiNK03259xUiMjcWqwGcsvtMQM9sKob7_LDWNalcYDFiLYmfVqmrHs7ZEWFdx9lr_AspRo27X8sMRgNYiaslhjfVgbB61XbHhRFo7czeYbLFlZSnMh6h60wunfGpqeWjLub4HEJG2BQhhQF6Oo2eTv_ULrZDawRTIUuK_u333KmgBJZd87vJLaSyjnds7Wx5oxFHX3r4hwL7RFbedMcZO86_OZQ0"
    }
  ]
}
```

```
cat ./keys/revoked-keys.json
```

```
{
  "keys": []
}
```


Now that we got our set of keys rotated, we will propagate them to our demo.

#### Use the rotated keys

In order to show you why we need the re-encrypt database batch, what I will do is first not call it. This way you will be able to observe the bad state of the database
and why it's essential to call this batch.

For simplicity, we will shutdown our demo first:

```
docker-compose down
```

Then only start the `db` and the `alice-application`

```
docker-compose up db alice-application
```


The first thing we will do is check the actuator endpoints to verify that alice got the new keys and not the old one:

```
curl --location --request GET 'http://localhost:8080/actuator/jose-database'
```

```
{
  "validKeys": {
    "keys": [
      {
        "kty": "RSA",
        "e": "AQAB",
        "use": "sig",
        "kid": "8e9bb522-bde6-4491-ae0b-3e92427c0ad2",
        "n": "kJSIcWZUhUvgontICnY-J33aNUHY0EBeQtLCU7pwzwVFR_8aYfJfZuiymqXxay8-FjN0Xp6bsntHzJEhmY_o0Lxdol_BWi4hsfWCGzrPJgMTrJQAlvBZNNrpxubJa8SUWADEaPj_I1YTFTB9pDdXC1Ty3lxoIEY5SsWDCnKa0pV9Z_5dZu6I4AIzJjxbyPJ17SkZgFQK-pQKGbZwhqZQWBvQFx1lZ5Wz3wrgOXC_zFJUn7nSUcqFAup9uaTNaylS_aCZL0ziyKPJuXIJ4I-17nhdtY4UzrIQCHUhSuJL3zqC9aPQiZ2nd9l9nHyy4ucPLH1OqAjdOkeFV3xjXPtCtzJDqUf6RYOQ9L9vkp0i_WqL9W1SUO_k0TIugTSCGyay71sN-OAc2HFZoG6nkdUsUWESgDpZHlcTfIZHRBkVnJqOXuU0nrBKMGTbYEKYeoZkHQa0Bm1X-hQ6zo5nduPCqXp1yy9pTUSl0QDP516D6WhIr57z8HCxSuf4YtEZZkC3-5SGMUgCbSfxbiE1vPCrUQxPj1ub1X8R65mF0_XI1O6F9WvHpFqcgIqaQTUF_jak3E_yxpifJ6twLvpvMsHyAf4W0etJ5Q0QVHgvR3_mttOnuglzjWGlZiW4pCzIufnhnKr6DvjrjAnOnunDaNyBwjtRf5fyKiJznYJbCzZFya8"
      },
      {
        "kty": "RSA",
        "e": "AQAB",
        "use": "enc",
        "kid": "494ea5dc-f8c7-4439-971f-5349a898d05c",
        "n": "gpvTjoNy-LmR_W-PHj1Lvq88YRaW5kLGUiyRwXUhSpiqkk6NFip8bDxa8TTgHxfVmJbGtgl4X-N6hTNZoNyklHTM9JZuxdIH1BGiYaIG3L15nfz8iGqdhB1UoW3X23-zLli6V5YVxn4XFypdrjZOrPndMVMLKV3ABGvVglLHGvREqDfg3a6g6kuGCeG2BnVTtyGCFmaDLlrd46ZscnlExJdFDOmoYaCZNabwhEnSAsEevTX9WDq4YSGdxZQoN2wdPKkgRnDuC_paYozjAVebEMfZY3fPK6VDVRkxFacO4_dIkQvplvOsfSs8utNO48ztubHoFD8dbD5ZkrVPEwlCzzVRgquksLahZwpBek1pQHQL402-7OlyvW-iQ9tM3ZKHQH7oNhuA1V72T8El1XVeZEz3ghuXSHMeuJHjYqVSaEUez1cG-5RNPO4_Nz2KTBVR1V834UFO-1IkokmsOdJ5T4iDal2BBlDWpanOvyCC_LGXH1J27W7pRZHUFUJApnXNq2KM9SfepJKZK6ieXXbMOtbOm17ZtXiJcoRvKrHWQ65rCVbsjB1z0mbQNPtk1PUhyj9CJ8wgToGie6xZAfOxkOAHV_pvLbOhRe0G17MtsrRS8w7-2THO9K9cUqbJZQhCBAgQlUhOPdKV9J5bUFIcZNlnPRCmwBQnGIopGu2SPbU"
      }
    ]
  },
  "expiredKeys": {
    "keys": [
      {
        "kty": "RSA",
        "e": "AQAB",
        "use": "sig",
        "kid": "4ef01c58-8b45-4874-bdb0-864eb5ef7af6",
        "n": "mpWKCJHMEKF2sijLHINvvTbKbmk_APGtOYQRZiAaf_detrNStT6NR_4eaEkkCZrfDteOZZ6OvKWLp_hIkDyz3PV6bq1CdRhQL0yT_RVjJ-jRyMZYwgcldULJORnBZ6HIOFGnPoPT-FyAuE0IDW-ttoGrpAF_IAsS1hMGCTI5xo97HbfMlKu93w8FfWY3-ke8JsjRF7jc5nNYmjzrM90OGiKBXfM84aajY3SEzWhuxzXTSTTT4PqkgaBI7vDu9O-qifDhxE9ZNIPBpVbj3YNw9443LuA1Ylp5xaGQdWyFOZV0CFtGX4FphlZsA5te3gPd0Dhn9vR1SVLk06T6GD5eUII5LfYP0ROcbCH4bpq8mvgJZFcsBhH6ua9k0EvEymSgpEH5AeHCnizWk-2rh_rk441i1wEPxikdqQQWj-f6xgmcFowmF-IGg6OGZ7iECZYZHtUdM9MzuVMZ49VKjJ7mbGd9WSEdSKzEr4UhMACGy9pbj4Nk6GAwMwb3N1WhpX7D6bTub5uspoKx-BaDde8IViOahLsIiKzCIATtMHAhR4l17R_UKEQ7ebpxwfGhDrrLK2Ih1fYpr4mk4MWwYoqvA7FEtaqBHigOwNwAutLCxcn8C6dvBbyPjg8agfKz3Ex5jzZfl1OvxC41DBY4SEGArGOZpnaFMrs7OcfSeEAMYz0"
      },
      {
        "kty": "RSA",
        "e": "AQAB",
        "use": "enc",
        "kid": "ed24f836-5e22-4e0c-b9b2-dc4ee85128a9",
        "n": "oxeuO_A0pBC-yUCOAVYN10H9HOz34-jksG_MG4rPRSwi7esUG1NbZ9V7D286gfGAh_Z4rp2mh0FqObscpstMoOsy3vEwas4CAkbhLk8OEmiWrLnR_VuTEI8Dog0X8GoyhXFLzWa61UqFQuotizEyA03aXT4qC0CJRm4I3YuIzReZU20npVWNuTyAtuHtsgLafYzsC5L92kV_-Dmjhe17yYb-X_FppyWCxIlgxz3uPo-N4cxBxChr2jyKxhPPkGTEEI23YEpRdMINEyNijVrwSWOr44AfosTDVRQiApPqyNYzoznMaSl-HznYVdLnipWi07uaNp4w3EeRJuLxHwfdG3D6T22oGrHLv5Sgy3H-9UdyS0OLoElQfuN0Z1x_qX9wSAyKYyEDfTm5VfvosRJj-lQVJloZuAX03r3VbsKbdILT6eBQvBhOjZf_-JSRJb8kSlDYKX7Eyg_KfIbLiNK03259xUiMjcWqwGcsvtMQM9sKob7_LDWNalcYDFiLYmfVqmrHs7ZEWFdx9lr_AspRo27X8sMRgNYiaslhjfVgbB61XbHhRFo7czeYbLFlZSnMh6h60wunfGpqeWjLub4HEJG2BQhhQF6Oo2eTv_ULrZDawRTIUuK_u333KmgBJZd87vJLaSyjnds7Wx5oxFHX3r4hwL7RFbedMcZO86_OZQ0"
      }
    ]
  },
  "revokedKeys": {
    "keys": []
  },
  "currentEncryptionKey": {
    "kty": "RSA",
    "e": "AQAB",
    "use": "enc",
    "kid": "494ea5dc-f8c7-4439-971f-5349a898d05c",
    "n": "gpvTjoNy-LmR_W-PHj1Lvq88YRaW5kLGUiyRwXUhSpiqkk6NFip8bDxa8TTgHxfVmJbGtgl4X-N6hTNZoNyklHTM9JZuxdIH1BGiYaIG3L15nfz8iGqdhB1UoW3X23-zLli6V5YVxn4XFypdrjZOrPndMVMLKV3ABGvVglLHGvREqDfg3a6g6kuGCeG2BnVTtyGCFmaDLlrd46ZscnlExJdFDOmoYaCZNabwhEnSAsEevTX9WDq4YSGdxZQoN2wdPKkgRnDuC_paYozjAVebEMfZY3fPK6VDVRkxFacO4_dIkQvplvOsfSs8utNO48ztubHoFD8dbD5ZkrVPEwlCzzVRgquksLahZwpBek1pQHQL402-7OlyvW-iQ9tM3ZKHQH7oNhuA1V72T8El1XVeZEz3ghuXSHMeuJHjYqVSaEUez1cG-5RNPO4_Nz2KTBVR1V834UFO-1IkokmsOdJ5T4iDal2BBlDWpanOvyCC_LGXH1J27W7pRZHUFUJApnXNq2KM9SfepJKZK6ieXXbMOtbOm17ZtXiJcoRvKrHWQ65rCVbsjB1z0mbQNPtk1PUhyj9CJ8wgToGie6xZAfOxkOAHV_pvLbOhRe0G17MtsrRS8w7-2THO9K9cUqbJZQhCBAgQlUhOPdKV9J5bUFIcZNlnPRCmwBQnGIopGu2SPbU"
  },
  "currentSigningKey": {
    "kty": "RSA",
    "e": "AQAB",
    "use": "sig",
    "kid": "8e9bb522-bde6-4491-ae0b-3e92427c0ad2",
    "n": "kJSIcWZUhUvgontICnY-J33aNUHY0EBeQtLCU7pwzwVFR_8aYfJfZuiymqXxay8-FjN0Xp6bsntHzJEhmY_o0Lxdol_BWi4hsfWCGzrPJgMTrJQAlvBZNNrpxubJa8SUWADEaPj_I1YTFTB9pDdXC1Ty3lxoIEY5SsWDCnKa0pV9Z_5dZu6I4AIzJjxbyPJ17SkZgFQK-pQKGbZwhqZQWBvQFx1lZ5Wz3wrgOXC_zFJUn7nSUcqFAup9uaTNaylS_aCZL0ziyKPJuXIJ4I-17nhdtY4UzrIQCHUhSuJL3zqC9aPQiZ2nd9l9nHyy4ucPLH1OqAjdOkeFV3xjXPtCtzJDqUf6RYOQ9L9vkp0i_WqL9W1SUO_k0TIugTSCGyay71sN-OAc2HFZoG6nkdUsUWESgDpZHlcTfIZHRBkVnJqOXuU0nrBKMGTbYEKYeoZkHQa0Bm1X-hQ6zo5nduPCqXp1yy9pTUSl0QDP516D6WhIr57z8HCxSuf4YtEZZkC3-5SGMUgCbSfxbiE1vPCrUQxPj1ub1X8R65mF0_XI1O6F9WvHpFqcgIqaQTUF_jak3E_yxpifJ6twLvpvMsHyAf4W0etJ5Q0QVHgvR3_mttOnuglzjWGlZiW4pCzIufnhnKr6DvjrjAnOnunDaNyBwjtRf5fyKiJznYJbCzZFya8"
  },
  "encryptionMethod": "A256CBC-HS512"
}
```

You can see that our application has successfully loaded the new keys. 

Let's check that our database status endpoints says about the situation:

```
curl --location --request GET 'http://localhost:8080/database/status'
```

```
Status of the progress database at 2020-07-10 14:00:48
- Nb of raws: 3
-------------
Number of entries by key type:
- REVOKED : 0 ;
- VALID : 0 ;
- EXPIRED : 3 ;
-------------
Number of JWTs using keys:
- 4ef01c58-8b45-4874-bdb0-864eb5ef7af6 : 3 ;
- ed24f836-5e22-4e0c-b9b2-dc4ee85128a9 : 3 ;
```

As you would have expected, the current row hasn't been updated. It means we current got 3 rows that are using our old keys.

Let's verify that we can still read them:
```
curl --location --request GET 'http://localhost:8080/persons/'
```

```
[
    {
        "id": 1,
        "name": "UAjKogHhAa",
        "email": "UAjKogHhAa@yapily.com"
    },
    {
        "id": 2,
        "name": "SkhstwfFQT",
        "email": "SkhstwfFQT@yapily.com"
    },
    {
        "id": 3,
        "name": "SMGwETaAQY",
        "email": "SMGwETaAQY@yapily.com"
    }
]
```

Success!! This is an important feature from our application to allow a smooth rotation of the keys. This will allow us to rotate our keys without downtime.


What happen now if we create new persons? Let's check that:
```
curl --location --request POST 'http://localhost:8080/persons/random?nb-entries=3'
```

```
[
    {
        "id": 4,
        "name": "wDuFqLEOcf",
        "email": "wDuFqLEOcf@yapily.com"
    },
    {
        "id": 5,
        "name": "jhjqQvQAeu",
        "email": "jhjqQvQAeu@yapily.com"
    },
    {
        "id": 6,
        "name": "srFMplbMRX",
        "email": "srFMplbMRX@yapily.com"
    }
]
```

Let's check again the status of our database:

```
curl --location --request GET 'http://localhost:8080/database/status'
```

```
Status of the progress database at 2020-07-10 14:04:40
- Nb of raws: 6
-------------
Number of entries by key type:
- REVOKED : 0 ;
- VALID : 3 ;
- EXPIRED : 3 ;
-------------
Number of JWTs using keys:
- 494ea5dc-f8c7-4439-971f-5349a898d05c : 3 ;
- 8e9bb522-bde6-4491-ae0b-3e92427c0ad2 : 3 ;
- 4ef01c58-8b45-4874-bdb0-864eb5ef7af6 : 3 ;
- ed24f836-5e22-4e0c-b9b2-dc4ee85128a9 : 3 ;
```

You can see that our new entries got encrypted using the valid keys. Our application is therefore able to read fields encrypted with expired keys but new entries would be encrypted using the latest keys.
This is a good news as this assure us that once we will have migrated the old field with the latest key, no application will produce rows encrypted with the old keys.


### Step 3: Migrate our old fields by re-encrypting them with the new valid keys.

If you remember in step 2, we voluntary didn't ran the `jose-reencrypt-database` docker container. This was a good way to show the importance of this migration.

Let's fix the state of our database by running the re-encryption batch job:

```
docker-compose up jose-reencrypt-database
```

Note that the alice application is still up and running and potentially serving requests.

Once you see a logs from our batch saying `!!! JOB FINISHED!!`. It means we should be back to our feet with a good database status.
Let's check that now:

```
curl --location --request GET 'http://localhost:8080/database/status'
```

```
Status of the progress database at 2020-07-10 14:09:47
- Nb of raws: 6
-------------
Number of entries by key type:
- REVOKED : 0 ;
- VALID : 6 ;
- EXPIRED : 0 ;
-------------
Number of JWTs using keys:
- 494ea5dc-f8c7-4439-971f-5349a898d05c : 6 ;
- 8e9bb522-bde6-4491-ae0b-3e92427c0ad2 : 6 ;
```

Success!! Now the database got all the emails using the latest valid keys. 


### Conclusion

This complete our demo of the database fields encryption using docker. In the third part of our articles, we will see how
you can achieve the same on your kubernetes cluster.
