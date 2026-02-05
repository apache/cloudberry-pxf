# Running Automation on Linux

**Note:** This information was documented based on the steps taken to get automation running on a Debian Bookworm (12) system.
They are intended to be used in tandem with the information in the main README file.

## Locale Setup

Automation creates a Cloudberry database using the `ru_RU.CP1251` locale. You can generate the required locale files with

```sh
sudo sed -i.bak -e 's/# ru_RU.CP1251.*/ru_RU.CP1251 CP1251/' /etc/locale.gen
sudo locale-gen
```

After generating the locale, restart your Cloudberry cluster

```sh
source $GPHOME/greenplum_path.sh # For Cloudberry 2.0
source $GPHOME/cloudberry-env.sh # For Cloudberry 2.1+
gpstop -a
gpstart -a
```
