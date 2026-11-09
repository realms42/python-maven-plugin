%global srcname example_package

Name:           python3-example-package
Version:        %{dist_rpm_version}
Release:        %{rpm_release}%{?dist}
Summary:        Example package built by python-maven-plugin
License:        GPL-3.0-only
URL:            https://github.com/realms42/python-maven-plugin
Source0:        %{dist_file}
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

%changelog
* Mon Nov 09 2026 realms42 - 1.0.0-1
- Package example_package's wheel
