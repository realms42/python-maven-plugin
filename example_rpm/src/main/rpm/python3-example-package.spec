%global srcname example_package

Name:           python3-example-package
Version:        %{wheel_version}
Release:        1%{?dist}
Summary:        Example package built by python-maven-plugin
License:        GPL-3.0-only
URL:            https://github.com/realms42/python-maven-plugin
Source0:        %{wheel_file}
BuildArch:      noarch

BuildRequires:  python3-devel
BuildRequires:  pyproject-rpm-macros
BuildRequires:  python3-pip

%description
The example package's wheel, as built by Maven, installed with Fedora's pyproject macros.

%prep
%setup -q -c -T

%build
mkdir -p %{_pyproject_wheeldir}
cp -p %{SOURCE0} %{_pyproject_wheeldir}/

%install
%pyproject_install
%pyproject_save_files -L %{srcname}

%check
%pyproject_check_import

%files -f %{pyproject_files}
